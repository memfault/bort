cmake_minimum_required(VERSION 3.9)

project(reporting)

add_library(reporting SHARED
    reporting.cpp
    reporting.h
)

set_target_properties(reporting PROPERTIES
        CXX_STANDARD 17)

target_include_directories(reporting PRIVATE ../../deps/rapidjson-1.1.0/include ../libmflt-structuredlog)
