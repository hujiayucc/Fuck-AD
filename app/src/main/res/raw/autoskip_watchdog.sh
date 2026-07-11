#!/system/bin/sh

CONFIG_FILE="/data/user/0/com.hujiayucc.hook/no_backup/autoskip_daemon_config.json"
PROCESS_NAME="fkad-daemon"
PID_FILE="/data/local/tmp/fkad-daemon.pid"
DEFAULT_LOG_FILE="/data/user/0/com.hujiayucc.hook/no_backup/autoskip_watchdog.log"
DEFAULT_STATUS_FILE="/data/user/0/com.hujiayucc.hook/no_backup/autoskip_watchdog_status.json"

json_value() {
    key="$1"
    file="$2"
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$file" | head -n 1 | sed 's#\\/#/#g'
}

json_bool() {
    key="$1"
    file="$2"
    value=$(sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([^,}]*\).*/\1/p" "$file" | head -n 1 | tr -d '[:space:]')
    [ "$value" = "true" ]
}

json_int() {
    key="$1"
    file="$2"
    fallback="$3"
    value=$(sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" "$file" | head -n 1)
    if [ -n "$value" ]; then
        echo "$value"
    else
        echo "$fallback"
    fi
}

now_ms() {
    date +%s000
}

log_msg() {
    log_file="$1"
    shift
    mkdir -p "$(dirname "$log_file")"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$log_file"
    tail -n 80 "$log_file" > "$log_file.tmp" 2>/dev/null && mv "$log_file.tmp" "$log_file"
}

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

write_status() {
    status_file="$1"
    action="$2"
    service_enabled_value="$3"
    connected_value="$4"
    heartbeat_age="$5"
    recover_count_value="$6"
    last_recover_at="$7"
    mkdir -p "$(dirname "$status_file")"
    action_escaped=$(json_escape "$action")
    tmp_file="$status_file.tmp"
    cat > "$tmp_file" <<EOF
{"processName":"$PROCESS_NAME","pid":$$,"lastCheckAt":$(now_ms),"lastAction":"$action_escaped","serviceEnabled":$service_enabled_value,"connected":$connected_value,"heartbeatAgeSeconds":$heartbeat_age,"recoverCount":$recover_count_value,"lastRecoverAt":$last_recover_at}
EOF
    mv "$tmp_file" "$status_file"
    chmod 644 "$status_file" 2>/dev/null
}

service_enabled() {
    component="$1"
    enabled=$(settings get secure enabled_accessibility_services 2>/dev/null)
    case ":$enabled:" in
        *":$component:"*) return 0 ;;
        *) return 1 ;;
    esac
}

service_connected() {
    component="$1"
    dumpsys accessibility 2>/dev/null | grep -qi "$component"
}

last_heartbeat() {
    health_file="$1"
    if [ ! -f "$health_file" ]; then
        echo 0
        return
    fi
    json_int "lastHeartbeatAt" "$health_file" 0
}

merge_service() {
    component="$1"
    enabled=$(settings get secure enabled_accessibility_services 2>/dev/null)
    if [ "$enabled" = "null" ]; then
        enabled=""
    fi
    case ":$enabled:" in
        *":$component:"*) echo "$enabled" ;;
        "::") echo "$component" ;;
        *) echo "$enabled:$component" ;;
    esac
}

remove_service() {
    component="$1"
    enabled=$(settings get secure enabled_accessibility_services 2>/dev/null)
    if [ "$enabled" = "null" ]; then
        enabled=""
    fi
    result=""
    old_ifs="$IFS"
    IFS=":"
    for item in $enabled; do
        if [ -n "$item" ] && [ "$item" != "$component" ]; then
            if [ -n "$result" ]; then
                result="$result:$item"
            else
                result="$item"
            fi
        fi
    done
    IFS="$old_ifs"
    echo "$result"
}

rebind_service() {
    component="$1"
    without=$(remove_service "$component")
    settings put secure enabled_accessibility_services "$without"
    settings put secure accessibility_enabled 1
    sleep 1
    with_service=$(merge_service "$component")
    settings put secure enabled_accessibility_services "$with_service"
    settings put secure accessibility_enabled 1
}

loop_watchdog() {
    recover_count=0
    last_recover_at=0
    window_start=$(date +%s)
    while true; do
        if [ ! -f "$CONFIG_FILE" ]; then
            sleep 30
            continue
        fi

        log_file=$(json_value "logFile" "$CONFIG_FILE")
        [ -n "$log_file" ] || log_file="$DEFAULT_LOG_FILE"
        status_file=$(json_value "statusFile" "$CONFIG_FILE")
        [ -n "$status_file" ] || status_file="$DEFAULT_STATUS_FILE"
        if [ ! -f "$PID_FILE" ]; then
            echo $$ > "$PID_FILE"
        fi
        if ! json_bool "enabled" "$CONFIG_FILE"; then
            write_status "$status_file" "disabled" false false -1 "$recover_count" "$last_recover_at"
            log_msg "$log_file" "disabled"
            sleep 60
            continue
        fi

        component=$(json_value "serviceComponent" "$CONFIG_FILE")
        health_file=$(json_value "healthFile" "$CONFIG_FILE")
        interval=$(json_int "intervalSeconds" "$CONFIG_FILE" 20)
        stale=$(json_int "staleSeconds" "$CONFIG_FILE" 45)
        max_recover=$(json_int "maxRecoverPerHour" "$CONFIG_FILE" 3)
        reenable=false
        if json_bool "reenableWhenUserDisabled" "$CONFIG_FILE"; then
            reenable=true
        fi

        [ -n "$component" ] || { sleep "$interval"; continue; }
        current_sec=$(date +%s)
        if [ $((current_sec - window_start)) -ge 3600 ]; then
            window_start="$current_sec"
            recover_count=0
        fi

        if ! service_enabled "$component"; then
            if [ "$reenable" = "true" ] && [ "$recover_count" -lt "$max_recover" ]; then
                settings put secure accessibility_enabled 1
                settings put secure enabled_accessibility_services "$(merge_service "$component")"
                recover_count=$((recover_count + 1))
                last_recover_at=$(now_ms)
                write_status "$status_file" "reenabled" true false -1 "$recover_count" "$last_recover_at"
                log_msg "$log_file" "reenabled accessibility service"
            else
                write_status "$status_file" "disabled_by_user_or_limited" false false -1 "$recover_count" "$last_recover_at"
                log_msg "$log_file" "accessibility disabled by user or recovery limited"
            fi
            sleep "$interval"
            continue
        fi

        heartbeat=$(last_heartbeat "$health_file")
        if [ "$heartbeat" -gt 0 ]; then
            age=$((($(now_ms) - heartbeat) / 1000))
        else
            age=-1
        fi
        connected=false
        if service_connected "$component"; then
            connected=true
        fi

        if [ "$connected" = "true" ] && [ "$heartbeat" -gt 0 ] && [ "$age" -le "$stale" ]; then
            write_status "$status_file" "healthy" true true "$age" "$recover_count" "$last_recover_at"
            sleep "$interval"
            continue
        fi

        if [ "$recover_count" -ge "$max_recover" ]; then
            write_status "$status_file" "recovery_limited" true "$connected" "$age" "$recover_count" "$last_recover_at"
            log_msg "$log_file" "recovery limited connected=$connected heartbeat_age=${age}s"
            sleep "$interval"
            continue
        fi

        rebind_service "$component"
        recover_count=$((recover_count + 1))
        last_recover_at=$(now_ms)
        write_status "$status_file" "rebind_requested" true "$connected" "$age" "$recover_count" "$last_recover_at"
        log_msg "$log_file" "rebind requested connected=$connected heartbeat_age=${age}s"
        sleep "$interval"
    done
}

start_watchdog() {
    if [ -f "$PID_FILE" ]; then
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
            exit 0
        fi
        rm -f "$PID_FILE"
    fi
    nohup /system/bin/sh "$0" __daemon_child >/dev/null 2>&1 &
    echo $! > "$PID_FILE"
}

stop_watchdog() {
    if [ -f "$PID_FILE" ]; then
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$old_pid" ]; then
            kill "$old_pid" 2>/dev/null
        fi
        rm -f "$PID_FILE"
    fi
}

case "$1" in
    __daemon_child)
        loop_watchdog
        ;;
    stop)
        stop_watchdog
        ;;
    restart)
        stop_watchdog
        start_watchdog
        ;;
    start|"")
        start_watchdog
        ;;
    *)
        start_watchdog
        ;;
esac
