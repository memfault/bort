#include <functional>

#include <cerrno>
#include <cmath>
#include <csignal>

#include <poll.h>
#include <sys/signalfd.h>
#include <sys/timerfd.h>
#include <unistd.h>

#include "timeutil.h"

namespace structured {

uint64_t getTimeInMsSinceEpoch() {
    timespec spec{};
    clock_gettime(CLOCK_REALTIME, &spec);

    uint64_t epoch = spec.tv_sec;
    uint32_t ms = round(spec.tv_nsec / 1.0e6);
    if (ms > 999) {
        epoch++;
        ms = 0;
    }
    return epoch * 1000 + ms;
}

#define SIGNAL_FD_ID 0
#define TIMER_FD_ID 1
#define TIME_T_MAX (time_t)((UINTMAX_C(1) << ((sizeof(time_t) << 3) - 1)) - 1)

void detectTimeChanges(std::function<void(uint64_t)> onRtcChanged) {
    pollfd pfds[2];
    pollfd *timer = &pfds[TIMER_FD_ID];
    pollfd *signal = &pfds[SIGNAL_FD_ID];

    sigset_t sigset;
    sigemptyset(&sigset);
    sigaddset(&sigset, SIGTERM);
    sigaddset(&sigset, SIGINT);
    sigprocmask(SIG_SETMASK, &sigset, NULL);
    int sigfd = signalfd(-1, &sigset, 0);
    signal->fd = sigfd;
    signal->events = POLL_IN;

    itimerspec its = {};
    its.it_value.tv_sec = TIME_T_MAX;

    int timerfd = timerfd_create(CLOCK_REALTIME, TFD_NONBLOCK);
    timerfd_settime(timerfd, TFD_TIMER_ABSTIME | TFD_TIMER_CANCEL_ON_SET, &its, NULL);
    timer->fd = timerfd;
    timer->events = POLLIN;

    while (1) {
        timer->revents = 0;
        signal->revents = 0;

        poll(pfds, 2, -1);

        if (timer->revents & POLLIN) {
            uint64_t expirations;
            if (-1 == read(timer->fd, &expirations, sizeof(expirations)) && errno == ECANCELED) {
                onRtcChanged(getTimeInMsSinceEpoch());
            }
            timerfd_settime(timerfd, TFD_TIMER_ABSTIME | TFD_TIMER_CANCEL_ON_SET, &its, NULL);
        }

        if (signal->revents & POLLIN) {
            break;
        }
    }
    close(timer->fd);
    close(signal->fd);
}

}  // namespace structured
