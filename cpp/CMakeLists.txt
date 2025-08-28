cmake_minimum_required(VERSION 3.12)
project(qdb_ha_reads)

set(CMAKE_CXX_STANDARD 17)

find_package(PkgConfig REQUIRED)
pkg_check_modules(PQXX REQUIRED libpqxx)

add_executable(HAReads main.cpp)

target_include_directories(HAReads PRIVATE ${PQXX_INCLUDE_DIRS})
target_link_directories(HAReads PRIVATE ${PQXX_LIBRARY_DIRS})
target_link_libraries(HAReads PRIVATE ${PQXX_LIBRARIES})
