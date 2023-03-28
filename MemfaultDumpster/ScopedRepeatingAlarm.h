#pragma once

#include <atomic>
#include <chrono>
#include <functional>
#include <iostream>
#include <thread>
#include <poll.h>

#include <cstring>

#include <sys/eventfd.h>
#include <sys/timerfd.h>
#include <unistd.h>

#include "ContinuousLogcat.h"

namespace memfault {

static constexpr int kTimerFd = 0;
static constexpr int kEventFd = 1;
static constexpr int kTimerCount = 2;


/**
 * Calls a function closure repeatedly at a specific interval. The underlying
 * mechanism uses timerfd, so the kernel does the timer work while the thread
 * polls for changes. An evenfd is used as a termination signaling mechanism
 */
class ScopedRepeatingAlarm {
public:
  ScopedRepeatingAlarm(std::function<std::chrono::nanoseconds()> period_func, std::function<void()> func)
    : timer_fd_(timerfd_create(CLOCK_BOOTTIME, 0)),
      event_fd_(eventfd(0, 0)),
      period_func_(std::move(period_func)),
      func_(std::move(func)) {

    configure_interval();
    thread_ = std::thread(&ScopedRepeatingAlarm::run, this);
    pthread_setname_np(thread_.native_handle(), "alarm");
  }

  void configure_interval() {
    auto period = period_func_();
    auto seconds = std::chrono::duration_cast<std::chrono::seconds>(period);

    auto s = static_cast<time_t>(seconds.count());
    auto ns = static_cast<long>((period - seconds).count());
    itimerspec timer_spec{
      {s, ns},
      {s, ns},
    };

    pfds[kTimerFd].fd = timer_fd_;
    pfds[kTimerFd].events = POLLIN;
    pfds[kEventFd].fd = event_fd_;
    pfds[kEventFd].events = POLLIN;

    timerfd_settime(timer_fd_, 0, &timer_spec, nullptr);
  }

  void run() {
    while (true) {
      int ret = poll(pfds, kTimerCount, -1);
      if (ret == -1) {
        if (errno == EAGAIN) continue;
        else break;
      }

      if (pfds[kEventFd].revents & POLLIN) {
        break;
      }

      if (pfds[kTimerFd].revents & POLLIN) {
        do {
          uint64_t times_fired;
          ssize_t nread = read(timer_fd_, &times_fired, sizeof(uint64_t));
          if (nread == -1 && errno != EAGAIN) break;
        } while (errno == EAGAIN);
        func_();
        configure_interval();
      }
    }

    close(timer_fd_);
    close(event_fd_);
    ALOGT("clog: alarm: thread done");
  }

  ~ScopedRepeatingAlarm() {
    itimerspec disarm_spec{/* disarm timer */};
    timerfd_settime(timer_fd_, 0, &disarm_spec, nullptr);

    uint64_t event = 1; // dummy, just for signaling
    write(event_fd_, &event, sizeof(event));

    ALOGT("clog: alarm: joining");
    thread_.join();
    ALOGT("clog: alarm: joined");
  }

private:
  int timer_fd_;
  int event_fd_;
  pollfd pfds[2];
  std::function<std::chrono::nanoseconds()> period_func_;
  std::function<void()> func_;
  std::thread thread_;
};

}
