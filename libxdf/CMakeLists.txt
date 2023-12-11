# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.12)
PROJECT(generate_xdf)

add_library( # Sets the name of the library.
		generate_xdf

		# Sets the library as a shared library.
		SHARED

		# Provides a relative path to your source file(s).
		# Associated headers in the same location as their source
		# file are automatically included.
		"generate_xdf.cpp"
		)

set(CMAKE_SHARED_LINKER_FLAGS "-z muldefs")
