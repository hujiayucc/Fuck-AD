#!/system/bin/sh

CONFIG_FILE="/data/user/0/com.hujiayucc.hook/no_backup/autoskip_daemon_config.json"
PROCESS_NAME="fkad-daemon"
PID_FILE="/data/local/tmp/fkad-daemon.pid"
LOCK_DIR="/data/local/tmp/fkad-daemon.lock"
APP_DATA_DIR="/data/user/0/com.hujiayucc.hook/no_backup"
SERVICE_COMPONENT="com.hujiayucc.hook/com.hujiayucc.hook.autoskip.AutoSkipAccessibilityService"
SERVICE_COMPONENT_SHORT="com.hujiayucc.hook/.autoskip.AutoSkipAccessibilityService"
SERVICE_PACKAGE="com.hujiayucc.hook"
SERVICE_CLASS_NAME="AutoSkipAccessibilityService"
SERVICE_PROCESS_NAME="com.hujiayucc.hook:autoskip"
SERVICE_LABEL="Fuck AD"
HEALTH_FILE="$APP_DATA_DIR/autoskip_health.json"
LOG_FILE="$APP_DATA_DIR/autoskip_watchdog.log"
STATUS_FILE="$APP_DATA_DIR/autoskip_watchdog_status.json"
RECOVERY_FILE="$APP_DATA_DIR/autoskip_watchdog_recovery.json"
INTERVAL_SECONDS=20
STALE_SECONDS=45
MAX_RECOVER_PER_HOUR=3
STARTUP_GRACE_SECONDS=90
STATUS_REFRESH_SECONDS=60
REBIND_REMOVE_DELAY_SECONDS=1
REBIND_VERIFY_TIMEOUT_SECONDS=30
REBIND_VERIFY_POLL_SECONDS=1
PACKAGE_UNSTOP_VERIFY_TIMEOUT_SECONDS=5
PACKAGE_UNSTOP_VERIFY_POLL_SECONDS=1
PACKAGE_STOP_RESTORE_MAX_HEARTBEAT_AGE_SECONDS=90
RECOVERY_STATE_VERSION=2
STATUS_SCHEMA_VERSION=4
SETTINGS_LOCK_DIR="$APP_DATA_DIR/autoskip_accessibility_settings.lock"
SETTINGS_LOCK_OWNER_FILE="$SETTINGS_LOCK_DIR/owner"
SETTINGS_LOCK_OPERATION_FILE="$SETTINGS_LOCK_DIR/operation"
SETTINGS_LOCK_TOKEN_FILE="$SETTINGS_LOCK_DIR/token"
SETTINGS_LOCK_LEASE_FILE="$SETTINGS_LOCK_DIR/leaseUntilSeconds"
SETTINGS_LOCK_STARTED_FILE="$SETTINGS_LOCK_DIR/startedAtMillis"
SETTINGS_LOCK_LEASE_SECONDS=90
SETTINGS_LOCK_INIT_GRACE_SECONDS=5

STATUS_REASON=""
STATUS_PREVIOUS_SERVICE_PID=0
STATUS_SERVICE_PID=0
STATUS_LAST_CONNECTED_AT=0
STATUS_LAST_HEARTBEAT_AT=0
STATUS_VERIFICATION_ELAPSED_SECONDS=0
STATUS_ATTEMPT_ID=""
STATUS_ATTEMPT_OWNER=""
STATUS_ATTEMPT_STARTED_AT=0
STATUS_ATTEMPT_FINISHED_AT=0
STATUS_BEFORE_SERVICE_PID=0
STATUS_AFTER_SERVICE_PID=0
STATUS_BEFORE_BOUND=false
STATUS_AFTER_BOUND=false
STATUS_BEFORE_BINDING=false
STATUS_AFTER_BINDING=false
STATUS_BEFORE_CRASHED=false
STATUS_AFTER_CRASHED=false
STATUS_BEFORE_CONNECTED_AT=0
STATUS_AFTER_CONNECTED_AT=0
STATUS_BEFORE_HEARTBEAT_AT=0
STATUS_AFTER_HEARTBEAT_AT=0
STATUS_SETTINGS_LOCK_OWNER=""
STATUS_SETTINGS_LOCK_LEASE_UNTIL=0
SETTINGS_LOCK_HELD=false
SETTINGS_LOCK_TOKEN=""
REBIND_EXPECTED_SERVICES=""
REBIND_ROLLBACK_CONFLICT=false

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

is_decimal() {
    case "$1" in
        ''|*[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

is_positive_decimal() {
    is_decimal "$1" || return 1
    case "$1" in
        *[1-9]*) return 0 ;;
        *) return 1 ;;
    esac
}

decimal_normalize() {
    value="$1"
    is_decimal "$value" || return 1
    value=$(printf '%s\n' "$value" | sed 's/^0*//')
    [ -n "$value" ] || value=0
    printf '%s\n' "$value"
}

decimal_ge() {
    left=$(decimal_normalize "$1") || return 1
    right=$(decimal_normalize "$2") || return 1
    left_length=${#left}
    right_length=${#right}
    if [ "$left_length" -gt "$right_length" ]; then
        return 0
    fi
    if [ "$left_length" -lt "$right_length" ]; then
        return 1
    fi
    [ "$left" = "$right" ] && return 0
    [ "$left" \> "$right" ]
}

decimal_gt() {
    left=$(decimal_normalize "$1") || return 1
    right=$(decimal_normalize "$2") || return 1
    left_length=${#left}
    right_length=${#right}
    if [ "$left_length" -gt "$right_length" ]; then
        return 0
    fi
    if [ "$left_length" -lt "$right_length" ] || [ "$left" = "$right" ]; then
        return 1
    fi
    [ "$left" \> "$right" ]
}

# Health and recovery timestamps are written in milliseconds. Convert them by
# string slicing before any shell arithmetic so Android's 32-bit shell never
# evaluates a millisecond value.
epoch_ms_to_sec() {
    value="$1"
    is_decimal "$value" || return 1
    length=${#value}
    if [ "$length" -gt 10 ]; then
        value=${value%???}
    fi
    value=$(decimal_normalize "$value") || return 1
    [ "${#value}" -le 10 ] || return 1
    printf '%s\n' "$value"
}

heartbeat_age_seconds() {
    heartbeat_value="$1"
    current_sec_value="$2"
    heartbeat_sec_value=$(epoch_ms_to_sec "$heartbeat_value") || {
        echo -1
        return 0
    }
    is_positive_decimal "$heartbeat_value" || {
        echo -1
        return 0
    }
    is_positive_decimal "$heartbeat_sec_value" || {
        echo -1
        return 0
    }
    if decimal_gt "$heartbeat_sec_value" "$current_sec_value"; then
        echo -1
    else
        echo $((current_sec_value - heartbeat_sec_value))
    fi
}

now_sec() {
    date +%s
}

now_ms() {
    echo "$(now_sec)000"
}

settings_lock_is_active() {
    [ -d "$SETTINGS_LOCK_DIR" ] || return 1
    lock_now_sec=$(now_sec)
    lock_lease=$(cat "$SETTINGS_LOCK_LEASE_FILE" 2>/dev/null)
    lock_max_lease=$((lock_now_sec + SETTINGS_LOCK_LEASE_SECONDS * 2))
    if is_decimal "$lock_lease" && decimal_gt "$lock_lease" "$lock_now_sec" &&
        ! decimal_gt "$lock_lease" "$lock_max_lease"; then
        return 0
    fi
    if ! is_decimal "$lock_lease"; then
        lock_mtime=$(stat -c '%Y' "$SETTINGS_LOCK_DIR" 2>/dev/null)
        if is_decimal "$lock_mtime" && [ "$lock_mtime" -gt 0 ] &&
            [ $((lock_now_sec - lock_mtime)) -lt "$SETTINGS_LOCK_INIT_GRACE_SECONDS" ]; then
            return 0
        fi
    fi
    return 1
}

settings_lock_reclaim_if_expired() {
    [ -d "$SETTINGS_LOCK_DIR" ] || return 0
    settings_lock_is_active && return 0
    lock_observed_lease=$(cat "$SETTINGS_LOCK_LEASE_FILE" 2>/dev/null)
    lock_observed_token=$(cat "$SETTINGS_LOCK_TOKEN_FILE" 2>/dev/null)
    lock_current_lease=$(cat "$SETTINGS_LOCK_LEASE_FILE" 2>/dev/null)
    lock_current_token=$(cat "$SETTINGS_LOCK_TOKEN_FILE" 2>/dev/null)
    if [ "$lock_current_lease" = "$lock_observed_lease" ] &&
        [ "$lock_current_token" = "$lock_observed_token" ]; then
        rm -f "$SETTINGS_LOCK_OWNER_FILE" "$SETTINGS_LOCK_OPERATION_FILE" \
            "$SETTINGS_LOCK_TOKEN_FILE" "$SETTINGS_LOCK_LEASE_FILE" "$SETTINGS_LOCK_STARTED_FILE"
        rmdir "$SETTINGS_LOCK_DIR" 2>/dev/null
    fi
}

settings_lock_acquire() {
    lock_owner="$1"
    lock_operation="$2"
    lock_attempt_id="$3"
    [ "$SETTINGS_LOCK_HELD" = "true" ] && return 1
    SETTINGS_LOCK_HELD=false
    SETTINGS_LOCK_TOKEN=""
    settings_lock_is_active && return 1
    settings_lock_reclaim_if_expired
    mkdir "$SETTINGS_LOCK_DIR" 2>/dev/null || return 1

    lock_started_at_ms=$(now_ms)
    lock_started_at_sec=$(now_sec)
    lock_lease_until=$((lock_started_at_sec + SETTINGS_LOCK_LEASE_SECONDS))
    SETTINGS_LOCK_TOKEN="${lock_owner}-${lock_started_at_ms}-$$-${lock_attempt_id:-none}"
    printf '%s\n' "$lock_owner" > "$SETTINGS_LOCK_OWNER_FILE" || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    printf '%s\n' "$lock_operation" > "$SETTINGS_LOCK_OPERATION_FILE" || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    printf '%s\n' "$SETTINGS_LOCK_TOKEN" > "$SETTINGS_LOCK_TOKEN_FILE" || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    printf '%s\n' "$lock_lease_until" > "$SETTINGS_LOCK_LEASE_FILE" || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    printf '%s\n' "$lock_started_at_ms" > "$SETTINGS_LOCK_STARTED_FILE" || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    lock_app_uid=$(stat -c '%u' "$APP_DATA_DIR" 2>/dev/null)
    lock_app_gid=$(stat -c '%g' "$APP_DATA_DIR" 2>/dev/null)
    if ! is_decimal "$lock_app_uid" || ! is_decimal "$lock_app_gid"; then
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    fi
    chown "$lock_app_uid:$lock_app_gid" "$SETTINGS_LOCK_DIR" \
        "$SETTINGS_LOCK_OWNER_FILE" "$SETTINGS_LOCK_OPERATION_FILE" \
        "$SETTINGS_LOCK_TOKEN_FILE" "$SETTINGS_LOCK_LEASE_FILE" "$SETTINGS_LOCK_STARTED_FILE" 2>/dev/null || {
        rm -rf "$SETTINGS_LOCK_DIR"
        SETTINGS_LOCK_TOKEN=""
        return 1
    }
    chmod 700 "$SETTINGS_LOCK_DIR" 2>/dev/null
    chmod 600 "$SETTINGS_LOCK_OWNER_FILE" "$SETTINGS_LOCK_OPERATION_FILE" \
        "$SETTINGS_LOCK_TOKEN_FILE" "$SETTINGS_LOCK_LEASE_FILE" "$SETTINGS_LOCK_STARTED_FILE" 2>/dev/null
    SETTINGS_LOCK_HELD=true
    STATUS_SETTINGS_LOCK_OWNER="$lock_owner"
    STATUS_SETTINGS_LOCK_LEASE_UNTIL="$lock_lease_until"
    return 0
}

settings_lock_renew() {
    [ "$SETTINGS_LOCK_HELD" = "true" ] || return 0
    lock_current_token=$(cat "$SETTINGS_LOCK_TOKEN_FILE" 2>/dev/null)
    [ "$lock_current_token" = "$SETTINGS_LOCK_TOKEN" ] || {
        SETTINGS_LOCK_HELD=false
        return 1
    }
    lock_lease_until=$(( $(now_sec) + SETTINGS_LOCK_LEASE_SECONDS ))
    printf '%s\n' "$lock_lease_until" > "$SETTINGS_LOCK_LEASE_FILE" || return 1
    STATUS_SETTINGS_LOCK_LEASE_UNTIL="$lock_lease_until"
    return 0
}

settings_lock_release() {
    [ "$SETTINGS_LOCK_HELD" = "true" ] || return 0
    lock_current_token=$(cat "$SETTINGS_LOCK_TOKEN_FILE" 2>/dev/null)
    if [ "$lock_current_token" = "$SETTINGS_LOCK_TOKEN" ]; then
        rm -f "$SETTINGS_LOCK_OWNER_FILE" "$SETTINGS_LOCK_OPERATION_FILE" \
            "$SETTINGS_LOCK_TOKEN_FILE" "$SETTINGS_LOCK_LEASE_FILE" "$SETTINGS_LOCK_STARTED_FILE"
        rmdir "$SETTINGS_LOCK_DIR" 2>/dev/null
    fi
    SETTINGS_LOCK_HELD=false
    SETTINGS_LOCK_TOKEN=""
}

settings_lock_client_active() {
    settings_lock_is_active || return 1
    [ "$(cat "$SETTINGS_LOCK_OWNER_FILE" 2>/dev/null)" = "client" ]
}

log_msg() {
    mkdir -p "$APP_DATA_DIR"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG_FILE"
    tail -n 80 "$LOG_FILE" > "$LOG_FILE.tmp" 2>/dev/null && mv "$LOG_FILE.tmp" "$LOG_FILE"
}

log_state() {
    signature="$1"
    shift
    if [ "$signature" = "$last_log_signature" ]; then
        return
    fi
    last_log_signature="$signature"
    log_msg "$*"
}

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

write_status() {
    action="$1"
    service_enabled_value="$2"
    connected_value="$3"
    heartbeat_age="$4"
    recover_count_value="$5"
    last_recover_at_value="$6"
    action_escaped=$(json_escape "$action")
    status_reason_escaped=$(json_escape "$STATUS_REASON")
    status_attempt_id_escaped=$(json_escape "$STATUS_ATTEMPT_ID")
    status_attempt_owner_escaped=$(json_escape "$STATUS_ATTEMPT_OWNER")
    status_lock_owner_escaped=$(json_escape "$STATUS_SETTINGS_LOCK_OWNER")
    status_service_pid_value="$STATUS_SERVICE_PID"
    status_previous_service_pid_value="$STATUS_PREVIOUS_SERVICE_PID"
    status_last_connected_at_value="$STATUS_LAST_CONNECTED_AT"
    status_last_heartbeat_at_value="$STATUS_LAST_HEARTBEAT_AT"
    status_verification_elapsed_value="$STATUS_VERIFICATION_ELAPSED_SECONDS"
    status_attempt_started_at_value="$STATUS_ATTEMPT_STARTED_AT"
    status_attempt_finished_at_value="$STATUS_ATTEMPT_FINISHED_AT"
    status_before_service_pid_value="$STATUS_BEFORE_SERVICE_PID"
    status_after_service_pid_value="$STATUS_AFTER_SERVICE_PID"
    status_before_connected_at_value="$STATUS_BEFORE_CONNECTED_AT"
    status_after_connected_at_value="$STATUS_AFTER_CONNECTED_AT"
    status_before_heartbeat_at_value="$STATUS_BEFORE_HEARTBEAT_AT"
    status_after_heartbeat_at_value="$STATUS_AFTER_HEARTBEAT_AT"
    status_lock_lease_until_value="$STATUS_SETTINGS_LOCK_LEASE_UNTIL"
    is_decimal "$status_service_pid_value" || status_service_pid_value=0
    is_decimal "$status_previous_service_pid_value" || status_previous_service_pid_value=0
    is_decimal "$status_last_connected_at_value" || status_last_connected_at_value=0
    is_decimal "$status_last_heartbeat_at_value" || status_last_heartbeat_at_value=0
    is_decimal "$status_verification_elapsed_value" || status_verification_elapsed_value=0
    is_decimal "$status_attempt_started_at_value" || status_attempt_started_at_value=0
    is_decimal "$status_attempt_finished_at_value" || status_attempt_finished_at_value=0
    is_decimal "$status_before_service_pid_value" || status_before_service_pid_value=0
    is_decimal "$status_after_service_pid_value" || status_after_service_pid_value=0
    is_decimal "$status_before_connected_at_value" || status_before_connected_at_value=0
    is_decimal "$status_after_connected_at_value" || status_after_connected_at_value=0
    is_decimal "$status_before_heartbeat_at_value" || status_before_heartbeat_at_value=0
    is_decimal "$status_after_heartbeat_at_value" || status_after_heartbeat_at_value=0
    is_decimal "$status_lock_lease_until_value" || status_lock_lease_until_value=0
    tmp_file="$STATUS_FILE.tmp.$$"
    cat > "$tmp_file" <<EOF
{"version":$STATUS_SCHEMA_VERSION,"schemaVersion":$STATUS_SCHEMA_VERSION,"processName":"$PROCESS_NAME","pid":$$,"watchdogPid":$$,"lastCheckAt":$(now_ms),"lastAction":"$action_escaped","phase":"$action_escaped","reason":"$status_reason_escaped","serviceEnabled":$service_enabled_value,"connected":$connected_value,"heartbeatAgeSeconds":$heartbeat_age,"recoverCount":$recover_count_value,"lastRecoverAt":$last_recover_at_value,"previousServicePid":$status_previous_service_pid_value,"servicePid":$status_service_pid_value,"lastConnectedAt":$status_last_connected_at_value,"lastHeartbeatAt":$status_last_heartbeat_at_value,"verificationElapsedSeconds":$status_verification_elapsed_value,"attemptId":"$status_attempt_id_escaped","attemptOwner":"$status_attempt_owner_escaped","attemptStartedAt":$status_attempt_started_at_value,"attemptFinishedAt":$status_attempt_finished_at_value,"beforeServicePid":$status_before_service_pid_value,"afterServicePid":$status_after_service_pid_value,"beforeBound":$STATUS_BEFORE_BOUND,"afterBound":$STATUS_AFTER_BOUND,"beforeBinding":$STATUS_BEFORE_BINDING,"afterBinding":$STATUS_AFTER_BINDING,"beforeCrashed":$STATUS_BEFORE_CRASHED,"afterCrashed":$STATUS_AFTER_CRASHED,"beforeConnectedAt":$status_before_connected_at_value,"afterConnectedAt":$status_after_connected_at_value,"beforeHeartbeatAt":$status_before_heartbeat_at_value,"afterHeartbeatAt":$status_after_heartbeat_at_value,"settingsLockOwner":"$status_lock_owner_escaped","settingsLockLeaseUntil":$status_lock_lease_until_value}
EOF
    mv "$tmp_file" "$STATUS_FILE"
    chmod 644 "$STATUS_FILE" 2>/dev/null
}

write_status_if_needed() {
    action="$1"
    service_enabled_value="$2"
    connected_value="$3"
    heartbeat_age="$4"
    recover_count_value="$5"
    last_recover_at_value="$6"
    current_sec=$(now_sec)
    signature="$action|$service_enabled_value|$connected_value|$recover_count_value|$last_recover_at_value|$STATUS_REASON|$STATUS_PREVIOUS_SERVICE_PID|$STATUS_SERVICE_PID|$STATUS_LAST_CONNECTED_AT|$STATUS_LAST_HEARTBEAT_AT|$STATUS_VERIFICATION_ELAPSED_SECONDS|$STATUS_ATTEMPT_ID|$STATUS_ATTEMPT_FINISHED_AT|$STATUS_BEFORE_BOUND|$STATUS_AFTER_BOUND|$STATUS_BEFORE_BINDING|$STATUS_AFTER_BINDING|$STATUS_BEFORE_CRASHED|$STATUS_AFTER_CRASHED|$STATUS_SETTINGS_LOCK_OWNER|$STATUS_SETTINGS_LOCK_LEASE_UNTIL"
    if [ "$signature" != "$last_status_signature" ] || [ $((current_sec - last_status_write_sec)) -ge "$STATUS_REFRESH_SECONDS" ]; then
        write_status "$action" "$service_enabled_value" "$connected_value" "$heartbeat_age" "$recover_count_value" "$last_recover_at_value"
        last_status_signature="$signature"
        last_status_write_sec="$current_sec"
    fi
}

save_recovery_state() {
    tmp_file="$RECOVERY_FILE.tmp.$$"
    cat > "$tmp_file" <<EOF
{"version":$RECOVERY_STATE_VERSION,"windowStartAtSeconds":$window_start,"recoverCount":$recover_count,"lastRecoverAt":$last_recover_at}
EOF
    mv "$tmp_file" "$RECOVERY_FILE"
    chmod 644 "$RECOVERY_FILE" 2>/dev/null
}

load_recovery_state() {
    current_sec=$(now_sec)
    if [ -f "$RECOVERY_FILE" ]; then
        state_version=$(json_int "version" "$RECOVERY_FILE" 0)
        window_start=$(json_int "windowStartAtSeconds" "$RECOVERY_FILE" "$current_sec")
        recover_count=$(json_int "recoverCount" "$RECOVERY_FILE" 0)
        last_recover_at=$(json_int "lastRecoverAt" "$RECOVERY_FILE" 0)
    else
        state_version=0
        window_start="$current_sec"
        recover_count=0
        last_recover_at=0
    fi

    window_start=$(decimal_normalize "$window_start" 2>/dev/null) || window_start=0
    recover_count=$(decimal_normalize "$recover_count" 2>/dev/null) || recover_count=0
    if last_recover_sec=$(epoch_ms_to_sec "$last_recover_at" 2>/dev/null); then
        :
    else
        last_recover_sec=0
        last_recover_at=0
    fi

    invalid_state=false
    if [ "$state_version" != "$RECOVERY_STATE_VERSION" ] || ! is_positive_decimal "$window_start" || decimal_gt "$window_start" "$current_sec"; then
        invalid_state=true
    fi
    if is_positive_decimal "$last_recover_sec" && decimal_gt "$last_recover_sec" "$current_sec"; then
        invalid_state=true
    fi
    if [ "$invalid_state" = "true" ]; then
        window_start="$current_sec"
        recover_count=0
        last_recover_at=0
        save_recovery_state
    elif decimal_gt "$recover_count" "$MAX_RECOVER_PER_HOUR"; then
        recover_count="$MAX_RECOVER_PER_HOUR"
        save_recovery_state
    fi
}

refresh_recovery_window() {
    current_sec="$1"
    if [ $((current_sec - window_start)) -ge 3600 ]; then
        window_start="$current_sec"
        recover_count=0
        save_recovery_state
    fi
}

recovery_delay_seconds() {
    case "$recover_count" in
        0) echo 0 ;;
        1) echo 90 ;;
        2) echo 180 ;;
        *) echo 360 ;;
    esac
}

read_enabled_services() {
    enabled=$(settings --user 0 get secure enabled_accessibility_services 2>/dev/null) || return 1
    [ "$enabled" = "null" ] && enabled=""
    printf '%s\n' "$enabled"
}

read_accessibility_enabled() {
    enabled=$(settings --user 0 get secure accessibility_enabled 2>/dev/null) || return 1
    if [ "$enabled" = "null" ] || [ -z "$enabled" ]; then
        enabled=0
    fi
    printf '%s\n' "$enabled"
}

normalize_service_list() {
    value="$1"
    [ "$value" = "null" ] && value=""
    printf '%s' "$value" | tr -d '\r'
}

services_equal() {
    left=$(normalize_service_list "$1")
    right=$(normalize_service_list "$2")
    [ "$left" = "$right" ]
}

settings_put_services() {
    expected="$1"
    settings --user 0 put secure enabled_accessibility_services "$expected" || return 1
    actual=$(read_enabled_services) || return 1
    services_equal "$actual" "$expected"
}

settings_put_accessibility_enabled() {
    expected="$1"
    settings --user 0 put secure accessibility_enabled "$expected" || return 1
    actual=$(read_accessibility_enabled) || return 1
    [ "$actual" = "$expected" ]
}

service_list_contains() {
    service_list="$1"
    old_ifs="$IFS"
    IFS=":"
    for service_item in $service_list; do
        if [ "$service_item" = "$SERVICE_COMPONENT" ] ||
            [ "$service_item" = "$SERVICE_COMPONENT_SHORT" ]; then
            IFS="$old_ifs"
            return 0
        fi
    done
    IFS="$old_ifs"
    return 1
}

text_contains_service_component() {
    text_value="$1"
    printf '%s\n' "$text_value" | grep -Fq "$SERVICE_COMPONENT" && return 0
    printf '%s\n' "$text_value" | grep -Fq "$SERVICE_COMPONENT_SHORT"
}

service_enabled() {
    enabled=$(read_enabled_services) || return 1
    service_list_contains "$enabled" || return 1
    [ "$(read_accessibility_enabled)" = "1" ]
}

package_stopped() {
    dumpsys package "$SERVICE_PACKAGE" 2>/dev/null |
        grep -Eq '^[[:space:]]*User 0:.*[[:space:]]stopped=true([[:space:]]|$)'
}

package_stop_restore_candidate() {
    [ "$(read_accessibility_enabled)" = "1" ] || return 1
    package_stopped || return 1
    [ -f "$HEALTH_FILE" ] || return 1
    json_bool "serviceConnected" "$HEALTH_FILE" || return 1

    package_stop_restore_current_sec=$(now_sec)
    package_stop_restore_heartbeat=$(last_heartbeat)
    package_stop_restore_age=$(heartbeat_age_seconds "$package_stop_restore_heartbeat" "$package_stop_restore_current_sec")
    package_stop_restore_connected_at=$(last_connected_at)
    is_positive_decimal "$package_stop_restore_connected_at" || return 1
    [ "$package_stop_restore_age" -ge 0 ] &&
        [ "$package_stop_restore_age" -le "$PACKAGE_STOP_RESTORE_MAX_HEARTBEAT_AGE_SECONDS" ]
}

clear_package_stopped_state() {
    package_stopped || return 0
    am startservice --user 0 --include-stopped-packages -n "$SERVICE_COMPONENT_SHORT" >/dev/null 2>&1 || :

    package_unstop_elapsed=0
    while package_stopped; do
        if [ "$package_unstop_elapsed" -ge "$PACKAGE_UNSTOP_VERIFY_TIMEOUT_SECONDS" ]; then
            am stopservice --user 0 -n "$SERVICE_COMPONENT_SHORT" >/dev/null 2>&1
            return 1
        fi
        sleep "$PACKAGE_UNSTOP_VERIFY_POLL_SECONDS"
        package_unstop_elapsed=$((package_unstop_elapsed + PACKAGE_UNSTOP_VERIFY_POLL_SECONDS))
    done

    # Remove the temporary started-service request. AccessibilityManager will
    # establish the durable binding after the component is restored below.
    am stopservice --user 0 -n "$SERVICE_COMPONENT_SHORT" >/dev/null 2>&1
    return 0
}

# Some Android builds omit component names from the accessibility Bound list.
# Prefer ActivityManager connection records; the Bound-list fallback is used
# only when no target ConnectionRecord exists, never when all records are DEAD.
service_connection_records_all() {
    dumpsys activity services 2>/dev/null |
        grep -F "ConnectionRecord{" |
        grep -F "$SERVICE_PACKAGE/" |
        grep -F "$SERVICE_CLASS_NAME"
}

service_connection_records() {
    service_connection_records_all |
        grep -v -E '[[:space:]]DEAD([[:space:]]|$)'
}

accessibility_section() {
    section_name="$1"
    dumpsys accessibility 2>/dev/null |
        awk -v section="$section_name" '
            BEGIN { capture = 0 }
            {
                if (!capture && index($0, section) > 0) {
                    capture = 1
                    rest = substr($0, index($0, section) + length(section))
                    if (rest ~ /[^[:space:]]/) print rest
                    next
                }
                if (capture && $0 ~ /^[[:space:]]*(Bound services:|Binding services:|Crashed services:|Enabled services:|Client list info:)/) exit
                if (capture) print
            }
        '
}

service_crashed() {
    crashed_services=$(accessibility_section "Crashed services:")
    text_contains_service_component "$crashed_services"
}

accessibility_service_bound() {
    bound_services=$(accessibility_section "Bound services:")
    [ -n "$bound_services" ] || return 1
    text_contains_service_component "$bound_services" && return 0
    printf '%s\n' "$bound_services" | grep -Fq "Service[label=$SERVICE_LABEL,"
}

accessibility_service_binding() {
    binding_services=$(accessibility_section "Binding services:")
    text_contains_service_component "$binding_services"
}

service_connected() {
    service_crashed && return 1
    service_pid >/dev/null 2>&1 || return 1
    accessibility_service_bound || return 1
    all_connection_records=$(service_connection_records_all)
    if [ -n "$all_connection_records" ]; then
        printf '%s\n' "$all_connection_records" |
            grep -v -E '[[:space:]]DEAD([[:space:]]|$)' >/dev/null 2>&1
        return $?
    fi
    return 0
}

service_pid() {
    for pid in $(pidof "$SERVICE_PROCESS_NAME" 2>/dev/null); do
        case "$pid" in
            ''|*[!0-9]*) continue ;;
        esac
        [ -r "/proc/$pid/cmdline" ] || continue
        process_cmdline=$(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null)
        case "$process_cmdline" in
            *"$SERVICE_PROCESS_NAME"*)
                printf '%s\n' "$pid"
                return 0
                ;;
        esac
    done
    return 1
}

last_heartbeat() {
    if [ ! -f "$HEALTH_FILE" ]; then
        echo 0
        return
    fi
    json_int "lastHeartbeatAt" "$HEALTH_FILE" 0
}

last_connected_at() {
    if [ ! -f "$HEALTH_FILE" ]; then
        echo 0
        return
    fi
    json_int "lastConnectedAt" "$HEALTH_FILE" 0
}

observe_service() {
    observed_current_sec=$(now_sec)
    observed_heartbeat=$(last_heartbeat)
    observed_age=$(heartbeat_age_seconds "$observed_heartbeat" "$observed_current_sec")
    observed_bound=false
    if accessibility_service_bound; then
        observed_bound=true
    fi
    observed_binding=false
    if accessibility_service_binding; then
        observed_binding=true
    fi
    observed_crashed=false
    if service_crashed; then
        observed_crashed=true
    fi
    observed_connected=false
    if service_connected; then
        observed_connected=true
    fi
    observed_health_connected=false
    if json_bool "serviceConnected" "$HEALTH_FILE"; then
        observed_health_connected=true
    fi
    observed_last_connected_at=$(last_connected_at)
    observed_service_pid=$(service_pid 2>/dev/null)
    STATUS_SERVICE_PID="$observed_service_pid"
    STATUS_LAST_CONNECTED_AT="$observed_last_connected_at"
    STATUS_LAST_HEARTBEAT_AT="$observed_heartbeat"
    if [ -n "$STATUS_ATTEMPT_ID" ] && ! is_positive_decimal "$STATUS_ATTEMPT_FINISHED_AT"; then
        STATUS_AFTER_SERVICE_PID="$observed_service_pid"
        STATUS_AFTER_BOUND="$observed_bound"
        STATUS_AFTER_BINDING="$observed_binding"
        STATUS_AFTER_CRASHED="$observed_crashed"
        STATUS_AFTER_CONNECTED_AT="$observed_last_connected_at"
        STATUS_AFTER_HEARTBEAT_AT="$observed_heartbeat"
    fi
}

observed_service_healthy() {
    [ "$observed_connected" = "true" ] || return 1
    [ "$observed_bound" = "true" ] || return 1
    [ "$observed_binding" = "false" ] || return 1
    [ "$observed_crashed" = "false" ] || return 1
    [ "$observed_health_connected" = "true" ] || return 1
    is_positive_decimal "$observed_last_connected_at" || return 1
    is_positive_decimal "$observed_heartbeat" || return 1
    is_positive_decimal "$observed_service_pid" || return 1
    [ "$observed_age" -ge 0 ] && [ "$observed_age" -le "$STALE_SECONDS" ]
}

service_is_healthy_now() {
    observe_service
    observed_service_healthy
}

merge_service() {
    if [ "$#" -gt 0 ]; then
        enabled="$1"
    else
        enabled=$(read_enabled_services) || return 1
    fi
    if service_list_contains "$enabled"; then
        echo "$enabled"
    elif [ -z "$enabled" ]; then
        echo "$SERVICE_COMPONENT_SHORT"
    else
        echo "$enabled:$SERVICE_COMPONENT_SHORT"
    fi
}

remove_service() {
    if [ "$#" -gt 0 ]; then
        enabled="$1"
    else
        enabled=$(read_enabled_services) || return 1
    fi
    result=""
    old_ifs="$IFS"
    IFS=":"
    for item in $enabled; do
        if [ -n "$item" ] &&
            [ "$item" != "$SERVICE_COMPONENT" ] &&
            [ "$item" != "$SERVICE_COMPONENT_SHORT" ]; then
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

restore_rebind_state() {
    REBIND_ROLLBACK_ATTEMPTED=true
    current_services=$(read_enabled_services) || {
        REBIND_ROLLBACK_SUCCEEDED=false
        return 1
    }
    if [ -z "$REBIND_EXPECTED_SERVICES" ] || ! services_equal "$current_services" "$REBIND_EXPECTED_SERVICES"; then
        REBIND_ROLLBACK_CONFLICT=true
        STATUS_REASON="rollback_skipped_external_settings_change"
        REBIND_ROLLBACK_SUCCEEDED=false
        return 2
    fi
    settings_put_services "$original_services" || {
        REBIND_ROLLBACK_SUCCEEDED=false
        return 1
    }
    original_accessibility_enabled="${original_accessibility_enabled:-1}"
    current_accessibility_enabled=$(read_accessibility_enabled) || {
        REBIND_ROLLBACK_SUCCEEDED=false
        return 1
    }
    if [ "$current_accessibility_enabled" = "1" ] && [ "$original_accessibility_enabled" != "1" ]; then
        settings_put_accessibility_enabled "$original_accessibility_enabled" || {
            REBIND_ROLLBACK_SUCCEEDED=false
            return 1
        }
    fi
    REBIND_ROLLBACK_SUCCEEDED=true
    return 0
}

rebind_service() {
    REBIND_ROLLBACK_ATTEMPTED=false
    REBIND_ROLLBACK_SUCCEEDED=false
    REBIND_ROLLBACK_CONFLICT=false
    REBIND_EXPECTED_SERVICES=""
    if [ "$SETTINGS_LOCK_HELD" != "true" ]; then
        settings_lock_acquire "daemon" "rebind" "${STATUS_ATTEMPT_ID:-standalone}" || return 4
    fi
    if service_is_healthy_now; then
        return 2
    fi

    original_services=$(read_enabled_services) || return 1
    original_accessibility_enabled=$(read_accessibility_enabled) || return 1
    [ "$original_accessibility_enabled" = "1" ] || return 3
    service_list_contains "$original_services" || return 3
    without=$(remove_service "$original_services") || return 1

    REBIND_EXPECTED_SERVICES="$without"
    if ! settings_put_services "$without"; then
        restore_rebind_state
        return 1
    fi
    if ! settings_put_accessibility_enabled 1; then
        restore_rebind_state
        return 1
    fi

    sleep "$REBIND_REMOVE_DELAY_SECONDS"
    current_accessibility_enabled=$(read_accessibility_enabled) || {
        restore_rebind_state
        return 1
    }
    if [ "$current_accessibility_enabled" != "1" ]; then
        restore_rebind_state
        return 3
    fi
    current_services=$(read_enabled_services) || {
        restore_rebind_state
        return 1
    }
    merged_services=$(merge_service "$current_services") || {
        restore_rebind_state
        return 1
    }
    REBIND_EXPECTED_SERVICES="$merged_services"
    if ! settings_put_services "$merged_services"; then
        restore_rebind_state
        return 1
    fi
    if ! settings_put_accessibility_enabled 1; then
        restore_rebind_state
        return 1
    fi
    return 0
}

restore_service_after_package_stop() {
    REBIND_ROLLBACK_ATTEMPTED=false
    REBIND_ROLLBACK_SUCCEEDED=false
    REBIND_ROLLBACK_CONFLICT=false
    REBIND_EXPECTED_SERVICES=""
    if [ "$SETTINGS_LOCK_HELD" != "true" ]; then
        settings_lock_acquire "daemon" "restore_stopped_package" "${STATUS_ATTEMPT_ID:-standalone}" || return 4
    fi
    if service_is_healthy_now; then
        return 2
    fi
    package_stop_restore_candidate || return 3

    original_services=$(read_enabled_services) || return 1
    original_accessibility_enabled=$(read_accessibility_enabled) || return 1
    [ "$original_accessibility_enabled" = "1" ] || return 3
    if ! clear_package_stopped_state; then
        STATUS_REASON="package_unstop_failed"
        return 1
    fi

    # Re-read after clearing the stopped state so an external settings change
    # during the wake-up window is preserved by the existing rollback guard.
    original_services=$(read_enabled_services) || return 1
    original_accessibility_enabled=$(read_accessibility_enabled) || return 1
    [ "$original_accessibility_enabled" = "1" ] || return 3
    if service_list_contains "$original_services"; then
        return 0
    fi

    merged_services=$(merge_service "$original_services") || return 1
    REBIND_EXPECTED_SERVICES="$merged_services"
    if ! settings_put_services "$merged_services"; then
        restore_rebind_state
        return 1
    fi
    if ! settings_put_accessibility_enabled 1; then
        restore_rebind_state
        return 1
    fi
    return 0
}

timestamp_is_new() {
    timestamp_value="$1"
    baseline_value="$2"
    attempt_started_at="$3"
    is_positive_decimal "$timestamp_value" || return 1
    decimal_ge "$timestamp_value" "$attempt_started_at" || return 1
    if is_positive_decimal "$baseline_value"; then
        decimal_gt "$timestamp_value" "$baseline_value" || return 1
    fi
    return 0
}

wait_for_service_rebind() {
    attempt_started_at="$1"
    baseline_connected_at="$2"
    baseline_heartbeat_at="$3"
    previous_service_pid="$4"
    verification_elapsed=0
    if is_positive_decimal "$previous_service_pid"; then
        STATUS_PREVIOUS_SERVICE_PID="$previous_service_pid"
    else
        STATUS_PREVIOUS_SERVICE_PID=0
    fi
    STATUS_VERIFICATION_ELAPSED_SECONDS=0

    while [ "$verification_elapsed" -le "$REBIND_VERIFY_TIMEOUT_SECONDS" ]; do
        settings_lock_renew || {
            STATUS_REASON="settings_lock_lost"
            STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
            return 1
        }
        observe_service
        STATUS_VERIFICATION_ELAPSED_SECONDS="$verification_elapsed"
        if observed_service_healthy &&
            timestamp_is_new "$observed_last_connected_at" "$baseline_connected_at" "$attempt_started_at" &&
            timestamp_is_new "$observed_heartbeat" "$baseline_heartbeat_at" "$attempt_started_at"; then
            if is_positive_decimal "$STATUS_PREVIOUS_SERVICE_PID" && [ "$observed_service_pid" != "$STATUS_PREVIOUS_SERVICE_PID" ]; then
                STATUS_REASON="verified_pid_changed"
            else
                STATUS_REASON="verified_connection_refreshed"
            fi
            STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
            return 0
        fi
        [ "$verification_elapsed" -ge "$REBIND_VERIFY_TIMEOUT_SECONDS" ] && break
        sleep "$REBIND_VERIFY_POLL_SECONDS"
        verification_elapsed=$((verification_elapsed + REBIND_VERIFY_POLL_SECONDS))
    done

    observe_service
    STATUS_VERIFICATION_ELAPSED_SECONDS="$verification_elapsed"
    STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
    if [ "$observed_bound" != "true" ]; then
        STATUS_REASON="verify_bound_timeout"
    elif [ "$observed_binding" = "true" ]; then
        STATUS_REASON="verify_binding_timeout"
    elif [ "$observed_crashed" = "true" ]; then
        STATUS_REASON="verify_crashed"
    elif ! is_positive_decimal "$observed_service_pid"; then
        STATUS_REASON="verify_pid_timeout"
    elif ! timestamp_is_new "$observed_last_connected_at" "$baseline_connected_at" "$attempt_started_at"; then
        STATUS_REASON="verify_connection_timeout"
    elif ! timestamp_is_new "$observed_heartbeat" "$baseline_heartbeat_at" "$attempt_started_at"; then
        STATUS_REASON="verify_heartbeat_timeout"
    else
        STATUS_REASON="verify_health_timeout"
    fi
    return 1
}

user_zero_ready() {
    unlocked=$(cmd user is-user-unlocked 0 2>/dev/null | tr -d '\r')
    [ "$unlocked" = "true" ] && [ -d "$APP_DATA_DIR" ] && return 0
    ce_available=$(getprop sys.user.0.ce_available 2>/dev/null)
    case "$ce_available" in
        1|true) [ -d "$APP_DATA_DIR" ] && return 0 ;;
    esac
    [ -r "$CONFIG_FILE" ]
}

wait_until_system_ready() {
    while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 5
    done
    while ! user_zero_ready; do
        sleep 5
    done
}

cleanup_pid() {
    if [ -f "$PID_FILE" ] && [ "$(cat "$PID_FILE" 2>/dev/null)" = "$$" ]; then
        rm -f "$PID_FILE"
    fi
}

loop_watchdog() {
    trap 'settings_lock_release; cleanup_pid' EXIT
    trap 'exit 0' TERM INT HUP
    echo $$ > "$PID_FILE.tmp.$$"
    mv "$PID_FILE.tmp.$$" "$PID_FILE"
    wait_until_system_ready
    mkdir -p "$APP_DATA_DIR"

    last_status_signature=""
    last_status_write_sec=0
    last_log_signature=""
    ready_at=$(now_sec)
    load_recovery_state
    write_status "starting" false false -1 "$recover_count" "$last_recover_at"
    last_status_write_sec="$ready_at"
    log_state "starting" "watchdog ready for Android user 0"

    while true; do
        STATUS_REASON=""
        STATUS_PREVIOUS_SERVICE_PID=0
        STATUS_SERVICE_PID=0
        STATUS_LAST_CONNECTED_AT=0
        STATUS_LAST_HEARTBEAT_AT=0
        STATUS_VERIFICATION_ELAPSED_SECONDS=0
        STATUS_SETTINGS_LOCK_OWNER=""
        STATUS_SETTINGS_LOCK_LEASE_UNTIL=0

        if [ ! -f "$CONFIG_FILE" ]; then
            STATUS_REASON="config_missing"
            write_status_if_needed "config_missing" false false -1 "$recover_count" "$last_recover_at"
            log_state "config_missing" "daemon config missing"
            sleep 30
            continue
        fi

        if ! json_bool "enabled" "$CONFIG_FILE"; then
            STATUS_REASON="daemon_disabled"
            write_status_if_needed "disabled" false false -1 "$recover_count" "$last_recover_at"
            log_state "disabled" "daemon disabled"
            sleep 60
            continue
        fi

        current_sec=$(now_sec)
        refresh_recovery_window "$current_sec"

        recovery_mode="rebind"
        service_enabled_status=true
        if ! service_enabled; then
            if package_stop_restore_candidate; then
                recovery_mode="restore_stopped_package"
                service_enabled_status=false
            else
                STATUS_REASON="disabled_by_user"
                write_status_if_needed "disabled_by_user" false false -1 "$recover_count" "$last_recover_at"
                log_state "disabled_by_user" "accessibility service disabled by user"
                sleep "$INTERVAL_SECONDS"
                continue
            fi
        fi

        observe_service
        current_sec="$observed_current_sec"
        heartbeat="$observed_heartbeat"
        age="$observed_age"
        connected="$observed_connected"

        if observed_service_healthy; then
            STATUS_REASON="fresh_bound_heartbeat"
            write_status_if_needed "healthy" true true "$age" "$recover_count" "$last_recover_at"
            log_state "healthy" "accessibility service healthy pid=$observed_service_pid heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        if [ "$recovery_mode" != "restore_stopped_package" ] &&
            [ $((current_sec - ready_at)) -lt "$STARTUP_GRACE_SECONDS" ]; then
            STATUS_REASON="startup_grace"
            write_status_if_needed "startup_grace" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
            log_state "startup_grace" "waiting for framework startup connected=$connected health_connected=$observed_health_connected heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        delay=$(recovery_delay_seconds)
        if last_recover_sec=$(epoch_ms_to_sec "$last_recover_at" 2>/dev/null); then
            :
        else
            last_recover_sec=0
        fi
        if [ "$recovery_mode" != "restore_stopped_package" ] &&
            is_positive_decimal "$last_recover_sec" &&
            [ $((current_sec - last_recover_sec)) -lt "$delay" ]; then
            STATUS_REASON="recovery_backoff"
            write_status_if_needed "recovery_backoff" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
            log_state "recovery_backoff_$recover_count" "recovery backoff connected=$connected health_connected=$observed_health_connected heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        if [ "$recover_count" -ge "$MAX_RECOVER_PER_HOUR" ]; then
            STATUS_REASON="recovery_limited"
            write_status_if_needed "recovery_limited" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
            log_state "recovery_limited_$recover_count" "recovery limited connected=$connected health_connected=$observed_health_connected heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        # Re-sample immediately before changing Secure Settings. A service that
        # became healthy during the previous checks must never be unbound.
        observe_service
        current_sec="$observed_current_sec"
        heartbeat="$observed_heartbeat"
        age="$observed_age"
        connected="$observed_connected"
        if observed_service_healthy; then
            STATUS_REASON="rebind_skipped_healthy"
            write_status_if_needed "healthy" true true "$age" "$recover_count" "$last_recover_at"
            log_state "rebind_skipped_healthy" "rebind skipped because service became healthy pid=$observed_service_pid heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        if settings_lock_client_active; then
            STATUS_REASON="client_settings_lock"
            STATUS_SETTINGS_LOCK_OWNER="client"
            STATUS_SETTINGS_LOCK_LEASE_UNTIL=$(cat "$SETTINGS_LOCK_LEASE_FILE" 2>/dev/null)
            write_status_if_needed "rebind_suppressed_client" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
            log_state "rebind_suppressed_client|$STATUS_SETTINGS_LOCK_LEASE_UNTIL" "rebind suppressed by client settings lease lease_until=$STATUS_SETTINGS_LOCK_LEASE_UNTIL connected=$connected heartbeat_age=${age}s"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        STATUS_ATTEMPT_ID="daemon-$(now_ms)-$$-$recover_count"
        STATUS_ATTEMPT_OWNER="daemon"
        STATUS_ATTEMPT_STARTED_AT=0
        STATUS_ATTEMPT_FINISHED_AT=0
        STATUS_BEFORE_SERVICE_PID=0
        STATUS_AFTER_SERVICE_PID=0
        STATUS_BEFORE_BOUND=false
        STATUS_AFTER_BOUND=false
        STATUS_BEFORE_BINDING=false
        STATUS_AFTER_BINDING=false
        STATUS_BEFORE_CRASHED=false
        STATUS_AFTER_CRASHED=false
        STATUS_BEFORE_CONNECTED_AT=0
        STATUS_AFTER_CONNECTED_AT=0
        STATUS_BEFORE_HEARTBEAT_AT=0
        STATUS_AFTER_HEARTBEAT_AT=0
        settings_operation="rebind"
        if [ "$recovery_mode" = "restore_stopped_package" ]; then
            settings_operation="restore_stopped_package"
        fi
        if ! settings_lock_acquire "daemon" "$settings_operation" "$STATUS_ATTEMPT_ID"; then
            STATUS_REASON="settings_lock_busy"
            STATUS_SETTINGS_LOCK_OWNER=$(cat "$SETTINGS_LOCK_OWNER_FILE" 2>/dev/null)
            STATUS_SETTINGS_LOCK_LEASE_UNTIL=$(cat "$SETTINGS_LOCK_LEASE_FILE" 2>/dev/null)
            write_status_if_needed "rebind_deferred_lock" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
            log_state "rebind_deferred_lock|$STATUS_SETTINGS_LOCK_OWNER|$STATUS_SETTINGS_LOCK_LEASE_UNTIL" "rebind deferred because settings lease is busy owner=$STATUS_SETTINGS_LOCK_OWNER lease_until=$STATUS_SETTINGS_LOCK_LEASE_UNTIL"
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        observe_service
        current_sec="$observed_current_sec"
        heartbeat="$observed_heartbeat"
        age="$observed_age"
        connected="$observed_connected"
        if observed_service_healthy; then
            STATUS_REASON="rebind_skipped_healthy"
            STATUS_ATTEMPT_STARTED_AT=$(now_ms)
            STATUS_ATTEMPT_FINISHED_AT="$STATUS_ATTEMPT_STARTED_AT"
            write_status_if_needed "healthy" true true "$age" "$recover_count" "$last_recover_at"
            log_state "rebind_skipped_healthy|$STATUS_ATTEMPT_ID" "rebind skipped after lease acquisition because service is healthy attemptId=$STATUS_ATTEMPT_ID pid=$observed_service_pid heartbeat_age=${age}s"
            settings_lock_release
            sleep "$INTERVAL_SECONDS"
            continue
        fi

        baseline_connected_at="$observed_last_connected_at"
        baseline_heartbeat_at="$observed_heartbeat"
        previous_service_pid="$observed_service_pid"
        if is_positive_decimal "$previous_service_pid"; then
            STATUS_PREVIOUS_SERVICE_PID="$previous_service_pid"
        else
            STATUS_PREVIOUS_SERVICE_PID=0
        fi
        STATUS_BEFORE_SERVICE_PID="$observed_service_pid"
        STATUS_BEFORE_BOUND="$observed_bound"
        STATUS_BEFORE_BINDING="$observed_binding"
        STATUS_BEFORE_CRASHED="$observed_crashed"
        STATUS_BEFORE_CONNECTED_AT="$observed_last_connected_at"
        STATUS_BEFORE_HEARTBEAT_AT="$observed_heartbeat"
        attempt_started_at=$(now_ms)
        STATUS_ATTEMPT_STARTED_AT="$attempt_started_at"
        log_msg "recovery_started mode=$recovery_mode attemptId=$STATUS_ATTEMPT_ID beforePid=${STATUS_BEFORE_SERVICE_PID:-0} beforeBound=$STATUS_BEFORE_BOUND beforeBinding=$STATUS_BEFORE_BINDING beforeCrashed=$STATUS_BEFORE_CRASHED beforeConnectedAt=$STATUS_BEFORE_CONNECTED_AT beforeHeartbeatAt=$STATUS_BEFORE_HEARTBEAT_AT"

        if [ "$recovery_mode" = "restore_stopped_package" ]; then
            restore_service_after_package_stop
        else
            rebind_service
        fi
        rebind_result=$?
        case "$rebind_result" in
            0)
                if wait_for_service_rebind "$attempt_started_at" "$baseline_connected_at" "$baseline_heartbeat_at" "$previous_service_pid"; then
                    if [ "$recovery_mode" = "restore_stopped_package" ]; then
                        action="package_stop_restore_verified"
                    else
                        action="rebind_verified"
                    fi
                else
                    if [ "$recovery_mode" = "restore_stopped_package" ]; then
                        action="package_stop_restore_unverified"
                    else
                        action="rebind_unverified"
                    fi
                fi
                observe_service
                ;;
            2)
                STATUS_REASON="rebind_skipped_healthy"
                STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
                write_status_if_needed "healthy" true true "$observed_age" "$recover_count" "$last_recover_at"
                log_state "rebind_skipped_healthy|$STATUS_ATTEMPT_ID" "rebind skipped by final health check attemptId=$STATUS_ATTEMPT_ID pid=$observed_service_pid heartbeat_age=${observed_age}s"
                settings_lock_release
                sleep "$INTERVAL_SECONDS"
                continue
                ;;
            3)
                if [ "$recovery_mode" = "restore_stopped_package" ]; then
                    STATUS_REASON="package_stop_restore_cancelled"
                else
                    STATUS_REASON="service_disabled_before_rebind"
                fi
                STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
                write_status_if_needed "disabled_by_user" false false -1 "$recover_count" "$last_recover_at"
                log_state "recovery_cancelled|$recovery_mode|$STATUS_ATTEMPT_ID" "recovery cancelled mode=$recovery_mode because the service state changed attemptId=$STATUS_ATTEMPT_ID"
                settings_lock_release
                sleep "$INTERVAL_SECONDS"
                continue
                ;;
            4)
                STATUS_REASON="settings_lock_busy"
                action="rebind_deferred_lock"
                settings_lock_release
                write_status_if_needed "$action" "$service_enabled_status" "$connected" "$age" "$recover_count" "$last_recover_at"
                log_state "$action|$STATUS_ATTEMPT_ID" "$action attemptId=$STATUS_ATTEMPT_ID owner=$(cat "$SETTINGS_LOCK_OWNER_FILE" 2>/dev/null)"
                sleep "$INTERVAL_SECONDS"
                continue
                ;;
            *)
                observe_service
                if [ "$recovery_mode" = "restore_stopped_package" ]; then
                    action="package_stop_restore_failed"
                else
                    action="rebind_failed"
                fi
                STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
                if [ "$REBIND_ROLLBACK_CONFLICT" = "true" ]; then
                    STATUS_REASON="settings_write_rollback_conflict"
                elif [ "$STATUS_REASON" = "settings_lock_lost" ]; then
                    :
                elif [ "$REBIND_ROLLBACK_ATTEMPTED" = "true" ]; then
                    if [ "$REBIND_ROLLBACK_SUCCEEDED" = "true" ]; then
                        STATUS_REASON="settings_write_failed_rollback_verified"
                    else
                        STATUS_REASON="settings_write_failed_rollback_failed"
                    fi
                else
                    STATUS_REASON="settings_read_or_write_failed"
                fi
                ;;
        esac

        settings_lock_release
        is_positive_decimal "$STATUS_ATTEMPT_FINISHED_AT" || STATUS_ATTEMPT_FINISHED_AT=$(now_ms)
        recover_count=$((recover_count + 1))
        last_recover_at=$(now_ms)
        save_recovery_state
        final_service_enabled=false
        if service_enabled; then
            final_service_enabled=true
        fi
        write_status_if_needed "$action" "$final_service_enabled" "$observed_connected" "$observed_age" "$recover_count" "$last_recover_at"
        log_state "$action|$STATUS_REASON|$recover_count|$STATUS_ATTEMPT_ID" "$action attemptId=$STATUS_ATTEMPT_ID reason=$STATUS_REASON oldPid=${STATUS_BEFORE_SERVICE_PID:-0} newPid=${STATUS_AFTER_SERVICE_PID:-0} beforeBound=$STATUS_BEFORE_BOUND afterBound=$STATUS_AFTER_BOUND beforeBinding=$STATUS_BEFORE_BINDING afterBinding=$STATUS_AFTER_BINDING beforeCrashed=$STATUS_BEFORE_CRASHED afterCrashed=$STATUS_AFTER_CRASHED beforeConnectedAt=$STATUS_BEFORE_CONNECTED_AT afterConnectedAt=$STATUS_AFTER_CONNECTED_AT beforeHeartbeatAt=$STATUS_BEFORE_HEARTBEAT_AT afterHeartbeatAt=$STATUS_AFTER_HEARTBEAT_AT heartbeatAge=${observed_age}s rollback_attempted=$REBIND_ROLLBACK_ATTEMPTED rollback_succeeded=$REBIND_ROLLBACK_SUCCEEDED"
        sleep "$INTERVAL_SECONDS"
    done
}

pid_matches_daemon() {
    pid="$1"
    [ -n "$pid" ] || return 1
    [ -r "/proc/$pid/cmdline" ] || return 1
    tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq "$0 __daemon_child"
}

start_watchdog() {
    if [ -f "$PID_FILE" ]; then
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if kill -0 "$old_pid" 2>/dev/null && pid_matches_daemon "$old_pid"; then
            return 0
        fi
        rm -f "$PID_FILE"
    fi

    if ! mkdir "$LOCK_DIR" 2>/dev/null; then
        sleep 1
        if [ -f "$PID_FILE" ]; then
            old_pid=$(cat "$PID_FILE" 2>/dev/null)
            if kill -0 "$old_pid" 2>/dev/null && pid_matches_daemon "$old_pid"; then
                return 0
            fi
        fi
        rm -rf "$LOCK_DIR"
        mkdir "$LOCK_DIR" 2>/dev/null || return 1
    fi

    nohup /system/bin/sh "$0" __daemon_child >/dev/null 2>&1 &
    child_pid=$!
    echo "$child_pid" > "$PID_FILE.tmp.$$"
    mv "$PID_FILE.tmp.$$" "$PID_FILE"
    rmdir "$LOCK_DIR" 2>/dev/null
}

stop_watchdog() {
    if [ -f "$PID_FILE" ]; then
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if pid_matches_daemon "$old_pid"; then
            kill "$old_pid" 2>/dev/null
            sleep 1
            if kill -0 "$old_pid" 2>/dev/null && pid_matches_daemon "$old_pid"; then
                kill -9 "$old_pid" 2>/dev/null
            fi
        fi
        rm -f "$PID_FILE"
    fi
    rm -rf "$LOCK_DIR"
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
