import org.apache.tools.ant.taskdefs.condition.Os

plugins {
   id("us.ihmc.ihmc-build")
}

ihmc {
   loadProductProperties("../group.gradle.properties")

   configureDependencyResolution()
   configurePublications()
}

allprojects {
   tasks.javadoc {
      exclude("us/ihmc/**")
   }
}

mainDependencies {
   api("us.ihmc:scs2-simulation:source")
   api("us.ihmc:scs2-session:source")
   api("us.ihmc:scs2-session-logger:source")
   api("us.ihmc:scs2-session-visualizer:source")

   var javaFXVersion = "17.0.8"
   api(ihmc.javaFXModule("base", javaFXVersion))
   api(ihmc.javaFXModule("controls", javaFXVersion))
   api(ihmc.javaFXModule("graphics", javaFXVersion))
   api(ihmc.javaFXModule("fxml", javaFXVersion))
   api(ihmc.javaFXModule("swing", javaFXVersion))

   api("us.ihmc:euclid:0.22.5")
   api("us.ihmc:euclid-shape:0.22.5")
   api("us.ihmc:euclid-frame:0.22.5")
   api("us.ihmc:ihmc-video-codecs:2.1.6")
   api("us.ihmc:ihmc-javafx-extensions:17-0.2.2")
   api("us.ihmc:ihmc-messager-javafx:0.2.1")

   api("org.reflections:reflections:0.9.11")

   // JavaFX extensions
   api("org.controlsfx:controlsfx:11.1.0")
   // TODO Switch away from the de.jensd to ikonli
   api("de.jensd:fontawesomefx-commons:9.1.2")
   api("de.jensd:fontawesomefx-octicons:4.3.0-9.1.2")
   api("de.jensd:fontawesomefx-materialicons:2.2.0-9.1.2")
   api("de.jensd:fontawesomefx-materialdesignfont:2.0.26-9.1.2")
   api("org.kordamp.ikonli:ikonli-javafx:12.3.1")
   api("org.kordamp.ikonli:ikonli-fontawesome-pack:12.3.1")
   api("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.3.1")
   api("us.ihmc:jfoenix:17-0.1.1")
   api("org.apache.commons:commons-text:1.9")

   api("us.ihmc:jim3dsModelImporterJFX:0.7")
   api("us.ihmc:jimColModelImporterJFX:0.6")
   api("us.ihmc:jimFxmlModelImporterJFX:0.5")
   api("us.ihmc:jimObjModelImporterJFX:0.8")
   api("us.ihmc:jimStlMeshImporterJFX:0.7")
   api("us.ihmc:jimX3dModelImporterJFX:0.4")

   // Dependencies for checking the version
   api("com.squareup.okhttp3:okhttp:4.12.0")
   api("com.google.code.gson:gson:2.10.1")

   api("me.tongfei:progressbar:0.10.0")
   api("commons-cli:commons-cli:1.6.0")

   // JavaCPP native library for macOS (Apple Silicon + Intel). The remote data-logger client
   // (us.ihmc.pubsub -> SerializedPayload -> org.bytedeco.javacpp.BytePointer) needs the JavaCPP JNI
   // native, but the us.ihmc:javacpp fork only publishes linux/windows natives. Without a macOS native
   // the remote connection dies with UnsatisfiedLinkError (BytePointer.allocateArray) and the session
   // is torn down immediately. These bytedeco natives are ABI-compatible with the 1.5.11 classes; only
   // the native artifact is pulled (isTransitive = false) so the existing javacpp classes are untouched.
   // installDistLinux/installDistWindows strip *-macosx-* so this only ships in the macOS build.
   api("org.bytedeco:javacpp:1.5.11:macosx-arm64") { isTransitive = false }
   api("org.bytedeco:javacpp:1.5.11:macosx-x86_64") { isTransitive = false }

   // lz4-java 1.8.0 bundles a darwin/aarch64 native; the transitively-pulled lz4 1.3.0 (2014) only has
   // darwin/x86_64, so log decompression falls back to the slow pure-Java LZ4 on Apple Silicon.
   api("org.lz4:lz4-java:1.8.0")
}

testDependencies {
   api("org.apache.commons:commons-math:2.2")
   api("org.testfx:openjfx-monocle:17.0.10")
   api("org.testfx:testfx-core:4.0.18")

}

categories.configure("javafx-headless")
{
   jvmArguments += "-Dtestfx.headless=true"
   jvmArguments += "-Dtestfx.robot=glass"
   jvmArguments += "-Djava.awt.headless=true"
   jvmArguments += "-Dprism.order=sw"
   jvmArguments += "-Dprism.verbose=true"
}

val sessionVisualizerExecutableName = "SCS2SessionVisualizer"
val mcapRepackAppExecutableName = "MCAPRepackApplication"
ihmc.jarWithLibFolder()
tasks.getByPath("installDist").dependsOn("compositeJar")
app.entrypoint(sessionVisualizerExecutableName, "us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizer", listOf("-Djdk.gtk.version=2", "-Dprism.vsync=false"))
app.entrypoint(mcapRepackAppExecutableName, "us.ihmc.scs2.sessionVisualizer.jfx.session.mcap.MCAPRepackApplication", listOf("-Djdk.gtk.version=2", "-Dprism.vsync=false"))

/**
 * This task is used to compile the project and filter out any dependency not required for Linux.
 */
tasks.register("installDistLinux") {
   dependsOn("installDist")

   doLast() {
      fileTree("${project.projectDir}/build/install/scs2-session-visualizer-jfx/lib").matching {
         include("*-win.jar")
         include("*-android-*")
         include("*-windows-*")
         include("*-ios-*")
         include("*-macosx-*")
         include("*-osx-*")
      }.forEach(File::delete)
   }
}

tasks.register("buildDebianPackage") {
   dependsOn("installDistLinux")

   doLast {
      val deploymentFolder = "${project.projectDir}/deployment"

      val debianFolder = "$deploymentFolder/debian"
      File(debianFolder).deleteRecursively()

      val baseFolder = "$deploymentFolder/debian/scs2-${ihmc.version}"
      val sourceFolder = "$baseFolder/opt/scs2-${ihmc.version}/"

      copy {
         from("${project.projectDir}/src/main/resources/icons/scs-icon.png")
         into("$sourceFolder/icon/")
      }

      copy {
         from("${project.projectDir}/build/install/scs2-session-visualizer-jfx/")
         into(sourceFolder)
      }

      fileTree("$sourceFolder/bin").matching {
         exclude(sessionVisualizerExecutableName, mcapRepackAppExecutableName)
      }.forEach(File::delete)

      addVSyncLinuxHackForJavaFXApp(sourceFolder, sessionVisualizerExecutableName)
      addVSyncLinuxHackForJavaFXApp(sourceFolder, mcapRepackAppExecutableName)

      File("$baseFolder/DEBIAN").mkdirs()
      println("Created directory $baseFolder/DEBIAN/: ${File("${baseFolder}/DEBIAN").exists()}")

      File("$baseFolder/DEBIAN/control").writeText(
            """
         Package: scs2
         Version: ${ihmc.version}
         Section: base
         Architecture: all
         Depends: default-jre (>= 2:1.17) | java17-runtime
         Maintainer: Sylvain Bertrand <sbertrand@ihmc.org>
         Description: Session Visualizer for SCS2
         Homepage: ${ihmc.vcsUrl}
         
         """.trimIndent()
      )

      File("$baseFolder/DEBIAN/postinst").writeText(
            """
         #!/bin/bash
         # Without this, the desktop file does not appear in the system menu.
         sudo desktop-file-install /usr/share/applications/scs2-${ihmc.version}-visualizer.desktop
         echo "-----------------------------------------------------------------------------------------------------------------------"
         echo "----------------------------------------------- Installation Notes: ---------------------------------------------------"
         echo "Add the following to your .bashrc to run SCS2 Session Visualizer form the command line:"
         echo "   export PATH=\${'$'}PATH:/opt/scs2-${ihmc.version}/bin/"
         echo "Then run the command '$sessionVisualizerExecutableName' to start the SCS2 Session Visualizer."
         echo "You can also run '$mcapRepackAppExecutableName' to start the MCAP Repack Application to help with corrupted MCAP files."
         echo "-----------------------------------------------------------------------------------------------------------------------"
         echo "-----------------------------------------------------------------------------------------------------------------------"
         """.trimIndent()
      )

      File("$baseFolder/usr/share/applications/").mkdirs()
      File("$baseFolder/usr/share/applications/scs2-${ihmc.version}-visualizer.desktop").writeText(
            """
         [Desktop Entry]
         Name=SCS2 Session Visualizer
         Comment=Session Visualizer for SCS2
         Exec=/opt/scs2-${ihmc.version}/bin/$sessionVisualizerExecutableName
         Icon=/opt/scs2-${ihmc.version}/icon/scs-icon.png
         Version=1.0
         Terminal=true
         Type=Application
         Categories=Utility;Application;
         """.trimIndent()
      )

      if (Os.isFamily(Os.FAMILY_UNIX))
      {
         ihmc.exec(ProcessBuilder("chmod", "+x", "$baseFolder/DEBIAN/postinst"))
         ihmc.exec(ProcessBuilder("chmod", "+x", "$sourceFolder/bin/$sessionVisualizerExecutableName"))
         ihmc.exec(ProcessBuilder("chmod", "+x", "$sourceFolder/bin/$mcapRepackAppExecutableName"))
         ihmc.exec(ProcessBuilder("dpkg", "--build", "scs2-${ihmc.version}").directory(File(debianFolder)))
      }
   }
}

tasks.register("installDistWindows") {
   dependsOn("installDist")

   doLast {
      fileTree("${project.projectDir}/build/install/scs2-session-visualizer-jfx/lib").matching {
         include("*-linux.jar")
         include("*-linux-*.jar")
         include("*-android-*")
         include("*-ios-*")
         include("*-mac.jar")
         include("*-mac-*.jar")
         include("*-macosx-*")
         include("*-osx-*")
      }.forEach(File::delete)
   }
}

tasks.register("buildWindowsMsiPackage") {
   dependsOn("installDistWindows")

   doLast {
      val deploymentFolder = "${project.projectDir}/deployment/windows"
      File(deploymentFolder).deleteRecursively()
      File(deploymentFolder).mkdirs()

      val libFolder = "${project.projectDir}/build/install/scs2-session-visualizer-jfx/lib"
      val jpackage = "${System.getProperty("java.home")}/bin/jpackage.exe"
      val mainJarName = "scs2-session-visualizer-jfx-${ihmc.version}.jar"
      // MSI requires x.y.z format; strip the leading Java-major prefix (e.g. "17-" from "17-0.33.0")
      val appVersion = ihmc.version.replace(Regex("^\\d+-"), "")
      val iconFile = "${project.projectDir}/src/main/resources/icons/scs-icon.ico"
      val wixBin = (System.getenv("WIX")?.trimEnd('\\')
         ?: File("C:\\Program Files (x86)").listFiles()
               ?.firstOrNull { it.name.startsWith("WiX Toolset") }
               ?.absolutePath
         ?: throw GradleException("WiX Toolset not found. Install WiX 3.x from https://github.com/wixtoolset/wix3/releases")) + "\\bin"

      // Phase 1: jpackage app-image (bundles JRE + both launchers into a directory)
      val appImageRoot = "${project.projectDir}/build/windows-app-image"
      File(appImageRoot).deleteRecursively()
      val mcapPropsFile = File("${project.projectDir}/build/mcap-launcher.properties")
      mcapPropsFile.writeText("main-class=us.ihmc.scs2.sessionVisualizer.jfx.session.mcap.MCAPRepackApplication\njava-options=-Dprism.vsync=false\n")
      ihmc.exec(ProcessBuilder(
         jpackage,
         "--type", "app-image",
         "--input", libFolder,
         "--dest", appImageRoot,
         "--name", sessionVisualizerExecutableName,
         "--main-class", "us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizer",
         "--main-jar", mainJarName,
         "--java-options", "-Dprism.vsync=false",
         "--add-launcher", "$mcapRepackAppExecutableName=${mcapPropsFile.absolutePath}",
         "--icon", iconFile
      ))

      // Phase 2: harvest app-image files with heat.exe
      val appImageDir = "$appImageRoot\\$sessionVisualizerExecutableName"
      val harvestWxs = "${project.projectDir}/build/AppImageFiles.wxs"
      ihmc.exec(ProcessBuilder(
         "$wixBin\\heat.exe", "dir", appImageDir,
         "-out", harvestWxs,
         "-cg", "AppImageFiles",
         "-dr", "INSTALLFOLDER",
         "-ke", "-srd", "-sreg", "-gg",
         "-var", "var.AppImageDir"
      ))

      // Phase 3: compile WiX sources
      val wixObjDir = "${project.projectDir}/build/wix-obj"
      File(wixObjDir).mkdirs()
      val wxsTemplate = "${project.projectDir}/src/main/wix/SCS2SessionVisualizer.wxs"
      ihmc.exec(ProcessBuilder(
         "$wixBin\\candle.exe",
         "-out", "$wixObjDir\\",
         "-dAppVersion=$appVersion",
         "-dIconFile=$iconFile",
         "-dAppImageDir=$appImageDir",
         wxsTemplate, harvestWxs
      ))

      // Phase 4: link MSI with WiX UI extension for feature selection dialog
      val msiFile = "$deploymentFolder\\${sessionVisualizerExecutableName}-${appVersion}.msi"
      ihmc.exec(ProcessBuilder(
         "$wixBin\\light.exe",
         "-ext", "WixUIExtension",
         "-out", msiFile,
         "$wixObjDir\\SCS2SessionVisualizer.wixobj",
         "$wixObjDir\\AppImageFiles.wixobj"
      ))
   }
}

/**
 * This task is used to compile the project and filter out any dependency not required for macOS.
 */
tasks.register("installDistMac") {
   dependsOn("installDist")

   doLast {
      fileTree("${project.projectDir}/build/install/scs2-session-visualizer-jfx/lib").matching {
         include("*-win.jar")
         include("*-windows-*")
         include("*-android-*")
         include("*-ios-*")
         include("*-linux.jar")
         include("*-linux-*.jar")
      }.forEach(File::delete)
   }
}

/**
 * Builds a native macOS .dmg installer via jpackage. On macOS, jpackage bundles the JRE, both
 * launchers, and the app-image straight into a .dmg in a single step (no external tooling like the
 * WiX toolchain used for the Windows MSI). Requires running on macOS with a JDK that ships jpackage.
 * Run with: gradle buildMacDmgPackage
 */
tasks.register("buildMacDmgPackage") {
   dependsOn("installDistMac")

   doLast {
      if (!Os.isFamily(Os.FAMILY_MAC))
         throw GradleException("buildMacDmgPackage must be run on macOS.")

      val deploymentFolder = "${project.projectDir}/deployment/macos"
      File(deploymentFolder).deleteRecursively()
      File(deploymentFolder).mkdirs()

      val libFolder = "${project.projectDir}/build/install/scs2-session-visualizer-jfx/lib"
      val jpackage = "${System.getProperty("java.home")}/bin/jpackage"
      val mainJarName = "scs2-session-visualizer-jfx-${ihmc.version}.jar"

      // macOS CFBundleVersion accepts at most three integers and the first must be > 0.
      // Strip the leading Java-major prefix (e.g. "17-" from "17-0.32.1.4"), keep the first three
      // numeric components, and fall back to the Java-major as the leading number if it would be 0.
      val javaMajor = Regex("^(\\d+)-").find(ihmc.version)?.groupValues?.get(1) ?: "1"
      val numericParts = ihmc.version.replace(Regex("^\\d+-"), "").split('.').take(3).toMutableList()
      if (numericParts.firstOrNull() == "0") numericParts[0] = javaMajor
      val appVersion = numericParts.joinToString(".")

      // Generate the .icns icon from the existing PNG using the macOS iconutil pipeline.
      val srcPng = "${project.projectDir}/src/main/resources/icons/scs-icon.png"
      val iconsetDir = File("${project.projectDir}/build/scs-icon.iconset")
      iconsetDir.deleteRecursively(); iconsetDir.mkdirs()
      listOf(
         "icon_16x16.png" to 16, "icon_16x16@2x.png" to 32,
         "icon_32x32.png" to 32, "icon_32x32@2x.png" to 64,
         "icon_128x128.png" to 128, "icon_128x128@2x.png" to 256,
         "icon_256x256.png" to 256, "icon_256x256@2x.png" to 512,
         "icon_512x512.png" to 512, "icon_512x512@2x.png" to 1024
      ).forEach { (name, px) ->
         ihmc.exec(ProcessBuilder("sips", "-z", "$px", "$px", srcPng, "--out", "${iconsetDir}/$name"))
      }
      val icnsFile = "${project.projectDir}/build/scs-icon.icns"
      ihmc.exec(ProcessBuilder("iconutil", "-c", "icns", iconsetDir.absolutePath, "-o", icnsFile))

      // Secondary launcher for the MCAP repack utility, bundled alongside the main app.
      val mcapPropsFile = File("${project.projectDir}/build/mcap-launcher.properties")
      mcapPropsFile.writeText(
         "main-class=us.ihmc.scs2.sessionVisualizer.jfx.session.mcap.MCAPRepackApplication\n" +
         "java-options=-Dprism.vsync=false\n"
      )

      // Single jpackage invocation: app-image + JRE -> .dmg.
      ihmc.exec(ProcessBuilder(
         jpackage,
         "--type", "dmg",
         "--input", libFolder,
         "--dest", deploymentFolder,
         "--name", sessionVisualizerExecutableName,
         "--main-class", "us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizer",
         "--main-jar", mainJarName,
         "--app-version", appVersion,
         "--java-options", "-Dprism.vsync=false",
         "--add-launcher", "$mcapRepackAppExecutableName=${mcapPropsFile.absolutePath}",
         "--icon", icnsFile,
         "--vendor", "IHMC",
         "--mac-package-name", "SCS2"
      ))
   }
}

fun addVSyncLinuxHackForJavaFXApp(sourceFolder: String, javafxappname: String)
{
   val launchScriptFile = File("$sourceFolder/bin/$javafxappname")
   var originalScript = launchScriptFile.readText()
   originalScript = originalScript.replaceFirst(
         "#!/bin/sh", """
         #!/bin/bash
         # This is a workaround for a bug in JavaFX 17.0.1, disabling vsync to improve framerate with multiple windows.
         export __GL_SYNC_TO_VBLANK=0
         
      """.trimIndent()
   )

   launchScriptFile.delete()
   launchScriptFile.writeText(originalScript)
}
