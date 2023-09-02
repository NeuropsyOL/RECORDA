# LSL java bindings

The Java interface for the lab streaming layer should run on Window/Linux/MacOS, 32/64 bit.
The main class is `edu.ucsd.sccn.LSL`, which provides all necessary functions and sub-classes.

## Getting Started - Traditional Build

Users intending to use the liblsl Java interface for Android should skip ahead to the Android-specific section near the bottom.

Generally, to use the labstreaminglayer java interface in your program, you must:
* import the class `edu.ucsd.sccn.LSL`
* include the `jna-{version}.jar` package in your classpath
    * Tested with version 5.6.0
* have the native liblsl library for your platform in a findable path
    * e.g., put liblsl64.dll into your application's root directory, or a system folder.
    * The section of code that looks for the shared library is [here](https://github.com/labstreaminglayer/liblsl-Java/blob/master/src/edu/ucsd/sccn/LSL.java#L1100-L1117). This may simplify in the future and the expected filename might change on Windows to `lsl.dll` to match standard naming conventions.

See the contents of `javadoc/` for API documentation.

### Examples

See `src/examples/` for quick examples of using `edu.ucsd.sccn.LSL`.

The instructions for the provided examples assume you have configured your system as follows:
* You have installed the JDK and its executables (`javac`, `java`) are on the path.
    * Android Studio users can add its `jre/bin` folder to the PATH
        * e.g. Windows: `set PATH=%PATH%;C:\Program Files\Android\Android Studio\jre\bin`
* [Download the jna jar](https://mvnrepository.com/artifact/net.java.dev.jna/jna) to the root `liblsl-Java` folder.
    * Click on your version, then find the download link in Files: jar
    * Tested with version 5.6.0
* Copy the LSL library for your system (e.g., `liblsl64.dll`) to the root `liblsl-Java` folder.
    * May be downloaded from the [liblsl releases page](https://github.com/sccn/liblsl/releases).

Compile the example (JDK binaries `javac` and `java` must be on path):
* `./liblsl-Java$ javac -cp jna-5.6.0.jar src/edu/ucsd/sccn/LSL.java src/examples/SendData.java`
    * Swap out SendData.java for another example that you are interested in.

Launch the example:
* Linux: `./liblsl-Java$ java -Djna.nosys=true -cp "jna-5.6.0.jar:src" examples.SendData`
* Windows: `./liblsl-Java> java -cp "jna-5.6.0.jar;src" examples.SendData`
    * (Note the : changes to a ;)

Descriptions of the provided example programs can be found [in the online documentation](https://labstreaminglayer.readthedocs.io/dev/examples.html#java-example-programs-basic-to-advanced).

## Getting Started - Android

The `build.gradle` is used to build a binary for Android devices and can be used as part of the `liblsl-Android` project. The build script assumes that the liblsl repository contents can be found in a sister folder: `../liblsl`, and that you have a version of CMake >= 3.12 (path to CMake can be configured in local.properties file).

See the [liblsl-Android documentation for Android Studio](https://github.com/labstreaminglayer/liblsl-Android/tree/master/AndroidStudio) for more information.

## Troubleshooting

If you get an error about some class def not found:
* On some systems you may have to unzip the `jna-{version}.jar` file to make sure that the libraries contained in it are found by Java.

If you encounter an error like: `java.lang.UnsatisfiedLinkError: Unable to load library 'liblsl64.dll'`: `Native library (win32-x86-64/liblsl64.dll) not found in resource path`:
* make sure that your liblsl library is on a path found by the java runtime, e.g., in the path where you execute the java command. One location where it will always be found is a system library path, e.g., `Windows/system32` (not SysWOW64) on Windows. It is not recommended to keep the lsl lib here permanently but temporarily this can help during debugging.
