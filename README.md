# [<img src=".github/readme_logo.svg" height="64" alt="saker.build system">](https://saker.build "Saker.build system")

[![Build status](https://img.shields.io/azure-devops/build/sakerbuild/9de21fc8-f935-48f9-bd0a-666d204cbdcb/15/master)](https://dev.azure.com/sakerbuild/saker.build/_build) [![Latest version](https://mirror.nest.saker.build/badges/saker.build/version.svg)](https://nest.saker.build/package/saker.build "saker.build | saker.nest")

Saker.build is a language agnostic build system focusing on extensibility and scalable incremental builds. 

**Technology independent.** Saker.build is designed to be universal and support any kind of frameworks and languages. Starting with Java and C++, the additional supported technologies grow rapidly via plugins.

**Scalable speed.** The build system scales to tens of thousands of source files while snappily performing incremental builds. See our [performance measurements](https://saker.build/saker.build/doc/perfcomparison.html "Performance comparisons | saker.build") for details.

**Highly parallel build language.** Saker.build uses its own build language by default. It's designed to allow maximum parallelability while keeping it easy to read and write.

**Extensible.** Basically any part of the build system is customizable. You can write your own build tasks, task repositories, or even scripting languages.

**Great IDE support.** You can run builds and edit your builds scripts with ease in your favorite IDE. Syntax highlight, code completion, and inline task documentation available in your editor. Official plugins are available for [IntelliJ](https://saker.build/saker.build/doc/intellijplugin.html "IntelliJ plugin | saker.build") and [Eclipse](https://saker.build/saker.build/doc/eclipseplugin.html "Eclipse plugin | saker.build").

**Distributed builds.** There's no limit for build performance as saker.build allows you to connect multiple computers and distribute the workload among them, greatly reducing build times.

**Record your builds.** [Build trace](https://saker.build/saker.build/doc/guide/buildtrace.html "Build trace | saker.build") is a recording of your build execution. You can view and analyze it to diagnose performanec bottlenecks or possible errors.

Documentation and more information is available at: [https://saker.build](https://saker.build "Saker.build system - A model build system")

## Installation

Saker.build only requires Java 8+ to run. It is distributed as a single JAR file that contains all necessary classes to run the build system.

See [Installation guide](https://saker.build/saker.build/doc/installation.html "Installation | saker.build") for downloading or installing an IDE plugin.

## FAQ

**How are build tasks loaded by saker.build?**

Saker.build uses task repositories to load build tasks. When you reference a task in the build script, it will ask the configured task repositories to find it. The task repositories themselves can load the tasks any way they want.

By default, saker.build loads build tasks from the [saker.nest](https://nest.saker.build/) task repository, via the internet. This enables updating plugins without updating the build system itself. (I.e. a bugfix in the Java plugin doesn't require an update of saker.build.)

**What technologies/languages are supported?**

At the time of writing, Java, C++ (with MSVC), Maven support is available, while Android and clang support is in development. They are all available via the [saker.nest](https://nest.saker.build/) repository.

**Are there any examples?**

See this [collection of examples](https://saker.build/saker.build/doc/examplecollection.html "Example collection | saker.build") that we've collected. They aren't complete starter projects, but snippets of build script code that help you get started.

**How do I write build scripts?**

Open an IDE, [install one of our plugins](https://saker.build/saker.build/doc/installation.html "Installation | saker.build") and start typing in a build script file. (Usually named saker.build.)

Make sure to extensively use the code completion/content assistant when writing build scripts, as they should guide your hand. If you're using IntelliJ, we recommend turning on the automatic documentation pupup.

![Code completion example GIF](https://saker.build/res/gfx/code_completion.gif)

**Can I use distributed builds automatically for all build tasks?**

No. Distributed builds are only supported for build tasks that themselves support them. The [MSVC plugin](https://saker.build/saker.msvc/doc/ccompile/buildclusters.html "Build clusters | saker.msvc") is one notable example that supports this.

**Does saker.build support incremental Java compilation and annotation processing?**

Yes. We've spent significant effort to implement Java compilation such way that it can [perfectly handle](https://saker.build/saker.java.compiler/doc/featurecomparison.html "Feature comparison | saker.java.compiler") incremental annotation processing out of the box, without the explicit need of support from the processor implementation.

**Does viewing a build trace require internet connection?**

No. Unlike other similar solutions on the market, recording a build trace and [viewing it](https://saker.build/buildtrace.html "Build trace viewer | saker.build") doesn't require you to transfer the build trace to a third party. While the [build trace viewer](https://saker.build/buildtrace.html "Build trace viewer | saker.build") is available on our website, it doesn't transmit any opened files.

**Is there a build cache?**

It's work in progress. See the [related issue](https://github.com/sakerbuild/saker.build/issues/10 "Build cache - Issue #10 - sakerbuild/saker.build").

**Does saker.build use timestamps or hashes for file change tracking?**

It uses timestamps + file size by default, but can be [configured](https://saker.build/saker.build/doc/guide/filechangetracking.html "File change tracking | saker.build") to use hashes. In our experience we've seen that timestamps haven't caused any errors for incremental builds.

You may need to switch to using hashes if you want to take advantage of the build cache.

**Is there a build daemon?**

Yes, you can optionally use a build daemon for your builds. They greatly speed up incremental builds as they keep data in memory and watch the file system for changes.

If you're using an IDE, you'll always use a build daemon, and that is embedded with the IDE plugin by default.

**Does saker.build support wildcards?**

Yes. You can use wildcard paths as the input for build tasks that support them.

Saker.build was built to support dynamically reported task dependencies in order to support use-cases such as reporting header file dependencies for C/C++ after the compilation is done.

**Why does the documentation feel fragmented?**

You may've noticed that each language/framework/functionality is documented on a different sub-site on [https://saker.build/](https://saker.build/ "Saker.build - A modern build system"). Java docs live on the [saker.java.compiler](https://saker.build/saker.java.compiler/doc/index.html "Overview | saker.java.compiler") site, while C++ with MSVC docs are on the [saker.msvc](https://saker.build/saker.msvc/doc/index.html "Overview | saker.msvc") pages.

Each sub-site corresponds to a package that implements that functionality. The build system 'saker.build' doesn't provide any of these packages, but they are available on the [saker.nest plugin repository](https://nest.saker.build/ "Saker.nest plugin repository"). \
Support for each langage/framework is available in its own package in order to separate responsibility. We didn't want to design a build system that includes all these features as that would've made it rigid. Saker.build can't actually build anything. It relies on external build task implementations to support doing anything significant.

To summarize, the documentation is fragmented, because the build task implementations are separated into different software packages.

**Why does it use the version 0.8.x?**

We've used 0.8.0 as the initial release instead of 0.1.0 in order to signal that saker.build is mature enough for use, but haven't reached API stability yet as 1.0.0 would imply.

See more about this [in our blog post](https://saker.build/blog/odd_initial_release_number.html "0.8.0 seems like an odd initial release number").

**Do the IDE plugins support dark theme?**

Yes.

![Dark script theme preview](https://saker.build/res/gfx/syntax_highlight_dual.png)

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
