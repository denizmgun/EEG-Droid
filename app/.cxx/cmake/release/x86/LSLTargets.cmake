# Generated by CMake

if("${CMAKE_MAJOR_VERSION}.${CMAKE_MINOR_VERSION}" LESS 2.5)
   message(FATAL_ERROR "CMake >= 2.6.0 required")
endif()
cmake_policy(PUSH)
cmake_policy(VERSION 2.6...3.17)
#----------------------------------------------------------------
# Generated CMake target import file.
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Protect against multiple inclusion, which would fail when already imported targets are added once more.
set(_targetsDefined)
set(_targetsNotDefined)
set(_expectedTargets)
foreach(_expectedTarget LSL::lsl)
  list(APPEND _expectedTargets ${_expectedTarget})
  if(NOT TARGET ${_expectedTarget})
    list(APPEND _targetsNotDefined ${_expectedTarget})
  endif()
  if(TARGET ${_expectedTarget})
    list(APPEND _targetsDefined ${_expectedTarget})
  endif()
endforeach()
if("${_targetsDefined}" STREQUAL "${_expectedTargets}")
  unset(_targetsDefined)
  unset(_targetsNotDefined)
  unset(_expectedTargets)
  set(CMAKE_IMPORT_FILE_VERSION)
  cmake_policy(POP)
  return()
endif()
if(NOT "${_targetsDefined}" STREQUAL "")
  message(FATAL_ERROR "Some (but not all) targets in this export set were already defined.\nTargets Defined: ${_targetsDefined}\nTargets not yet defined: ${_targetsNotDefined}\n")
endif()
unset(_targetsDefined)
unset(_targetsNotDefined)
unset(_expectedTargets)


# Create imported target LSL::lsl
add_library(LSL::lsl SHARED IMPORTED)

set_target_properties(LSL::lsl PROPERTIES
  INTERFACE_COMPILE_DEFINITIONS "\$<IF:\$<BOOL:OFF>,LIBLSL_STATIC,LIBLSL_EXPORTS>;\$<\$<CXX_COMPILER_ID:MSVC>:LSLNOAUTOLINK>"
  INTERFACE_INCLUDE_DIRECTORIES "/home/mvidaldepalo/github/eeg_droid_deniz/EEG-Droid/liblsl/include"
)

# Import target "LSL::lsl" for configuration "Release"
set_property(TARGET LSL::lsl APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(LSL::lsl PROPERTIES
  IMPORTED_LOCATION_RELEASE "/home/mvidaldepalo/github/eeg_droid_deniz/EEG-Droid/app/build/intermediates/cmake/release/obj/x86/liblsl.so"
  IMPORTED_SONAME_RELEASE "liblsl.so"
  )

# This file does not depend on other imported targets which have
# been exported from the same project but in a separate export set.

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
cmake_policy(POP)
