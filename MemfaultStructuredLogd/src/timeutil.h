#pragma once

#include <cstdint>
#include <ctime>

namespace structured {

/**
 * Obtains the time in milliseconds since epoch. This time is not
 * monotonic and can increase/decrease based NTP / user setup.
 *
 * @return The number of milliseconds since epoch.
 */
uint64_t getTimeInMsSinceEpoch();

/**
 * Runs a realtime clock detector loop via timerfd_settime. A timerfd is created
 * and time is set to max time since boot (will never happen). It uses the
 * TFD_TIMER_CANCEL_ON_SET which will return any blocked readers with -ECANCELED
 * if someone else sets the system time. This method uses poll on that fd and calls
 * back to a handler function when the time changes.
 *
 * In order to support graceful termination, it simultaneously polls a signalfd
 * set to read SIGTERM.
 *
 * This method will run until interrupted via SIGTERM, use with a thread.
 */
void detectTimeChanges(std::function<void(uint64_t)> onRtcChanged);

}