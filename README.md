# [<img src=".github/readme_logo.svg" height="64" alt="saker.build system">](https://saker.build "Saker.build system")

Saker.build is a language agnostic build system that focuses on extensibility and scalable incremental builds. It supports distributing build tasks over multiple build machines, rebuilding only the smallest possible part of the project, and can scale for tens of thousands of source files.

Saker.build uses its own scripting language for defining the build process and loads the build tasks from the configured task repositories. It integrates well with the [Eclipse IDE](Eclipse plugin | saker.build) and provides [unmatched performance for Java builds](https://saker.build/saker.java.compiler/doc/performancecomparison.html "Performance comparison | saker.java.compiler").

Visit [https://saker.build](https://saker.build "Saker.build system") for more information.

## Installation

Saker.build only requires Java 8+ to run. It is distributed as a single JAR file that contains all necessary classes to run the build system.

See [Installation guide](https://saker.build/saker.build/doc/installation.html "Installation | saker.build") for downloading or installing an IDE plugin.

## Repository structure

The Java source code of the build system is split up into multiple directories as follows:

* `core`: Contains the core implementation of saker.build.
* `launching`: Contains the Java classes that are used for the command line interface of saker.build.
* `internal`: Contains internal build system classes that are specially packaged with the release JAR:
	* The SakerScript default build language classes.
* `thirdparty`: Contains repackaged source files of third party libraries included in saker.build. All classes are in the `saker.build.thirdparty` package.
	* [ObjectWeb ASM](https://asm.ow2.io/ "ASM")
	* [saker.util](https://github.com/sakerbuild/saker.util "sakerbuild/saker.util")
	* [saker.rmi](https://github.com/sakerbuild/saker.rmi "sakerbuild/saker.rmi")
* `support`: Contains Java classes that are not part of the main build system release, but as supporting classes.
	* IDE implementation support classes.
* `test`: Contains the classes for testing.
* `native`: Contains C/C++ sources of the supporting native libraries.
	* Win32 and macOS implementations of file watch services.
	* The compiled versions of these sources are present in the `resources` directory. (Currently they are not built as part of the build system build process.)
* `resources`: Contains resources that are included in the distributed JARs.
* `build`: The build output directory, not checked into version control.

## Build instructions

Building saker.build requires both JDK8 and JDK9 to be installed. It is built by the latest version of the saker.build system.

To perform the build, you need to set the output build directory, and the location of the installed JDKs:

```
java -jar path/to/saker.build.jar -bd build -EUsaker.java.jre.install.locations=path/to/jdk8;path/to/jdk9 export saker.build
```

You can choose an appropriate target in `saker.build` to execute for your use-case. You may need other JDKs if you want to execute the tests on JDK10+.

The build can also be executed inside an IDE.

## Documentation

The documentation of saker.build is available at: [https://saker.build/saker.build/doc/index.html](https://saker.build/saker.build/doc/index.html "Overview | saker.build")

If you're looking for examples to build projects for a given language or use-case, see the [example collection](https://saker.build/saker.build/doc/examplecollection.html "Example collection | saker.build"). You can also search the [saker.nest repository](https://nest.saker.build "Saker.nest plugin repository") for a suitable plugin.

See the [extension guide](https://saker.build/saker.build/doc/extending/index.html) if you want to develop your own build tasks, task repositories, or build languages.

See the [script guide](https://saker.build/saker.build/doc/scripting/index.html) for information about the built-in build language.

## License

The source code for the project is licensed under *GNU General Public License v3.0 only*.

Short identifier: [`GPL-3.0-only`](https://spdx.org/licenses/GPL-3.0-only.html).

Official releases of the project (and parts of it) may be licensed under different terms. See the particular releases for more information.

## Contributing

See the [contribution guide](https://saker.build/saker.build/doc/contribute.html "Contribute | saker.build") for information about how you can help the development of saker.build.
