### Tor Installer

This library provides an installer for Tor binaries. The current version is 0.4.0.

The following platforms are supported:
* linux-i686
* linux-x86_64
* osx-x86_64
* windows-i686
* windows-x86_64

Android is not currently supported.

#### Using the library
This library is actor based. You will first need to create an actor that
will read messages coming back from the installer.

<pre>
fun installChannel() = GlobalScope.actor<Any> {
     for (message in channel) {
         when(message) {
             is InstallComplete -> {
                 println("Tor install complete")
             }
             is InstallError -> {
                 //react to error
                 println("Failed to install: $message")
             }
             else -> {
                 println(message)
             }
         }
     }
 }
 </pre>
 
 Now just installTor using your specified installation directory and the install 
 channel that will be used to receive messages back
 
 <pre>
     val installDir = File("tor-install")
     GlobalScope.launch {
         installTor(installDir, eventChannel = installChannel())
     }.start()
 </pre>
 
 #### Building the Installer

First make sure that you put in some dummy values into your gradle.properties file in the root
of the project. This is so the publishing repository metainfo won't fail the build with unrecognized properties. 

    username=dummy
    password=dummy
    
Now to build

    ./gradlew build
 
 #### Building the Tor binaries
 The binaries are built using RBM: 
 https://github.com/sisbell/i2peer-tor-build
 
 The above project is a modified version of the Tor Browser Build, pared down for just Tor
 release builds.
