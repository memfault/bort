cmake_minimum_required(VERSION 3.14)
project(my_project)

# GoogleTest requires at least C++14
set(CMAKE_CXX_STANDARD 14)

include(FetchContent)
FetchContent_Declare(
        googletest
        URL https://github.com/google/googletest/archive/03597a01ee50ed33e9dfd640b249b4be3799d395.zip
)

enable_testing()
find_package(GTest REQUIRED)
find_package(SQLite3 REQUIRED)
include_directories(${SQLITE3_INCLUDE_DIRS})

add_executable(
        structured_logd_tests
        src/config.cpp
        src/dumper.cpp
        src/logger.cpp
        src/logwriter.cpp
        src/metric_reporter.cpp
        src/metric_service.cpp
        src/storage.cpp
        src/timeutil.cpp

        tests/test_config.cpp
        tests/test_dumper.cpp
        tests/test_logger.cpp
        tests/test_logwriter.cpp
        tests/test_metric_reporter.cpp
        tests/test_metric_service.cpp
        tests/test_rate_limiter.cpp
        tests/test_storage.cpp)
target_include_directories(
        structured_logd_tests PRIVATE
        src/
        deps/sqlite_modern_cpp-3.2/hdr
        deps/rapidjson-1.1.0/include
)
target_link_libraries(
        structured_logd_tests
        sqlite3
        GTest::gtest_main
        gmock
)

include(GoogleTest)
gtest_discover_tests(structured_logd_tests)
