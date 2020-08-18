Title: Homebrew: write your own brew formula

[GraalVM](https://www.graalvm.org/) is a recent development in the Java ecosystem that allows you to generate native binaries for applications that run on the Java Virtual Machine (JVM). One of the main advantages of this is that it gets around the JVMs slow startup time which is a problem for short lived programs that are run often. This has lead to a projects like [zprint releasing native binaries](https://github.com/kkinnear/zprint/releases/tag/1.0.0). This is great but, it doesn't give you a nice reproducible way to install/manage/uninstall these executables. For that we want a package manager like [homebrew](https://brew.sh/).

To install a package via homebrew you need to have a brew formula. A formula is a package definition written in Ruby. There isn't currently a brew formula for zprint so we will need to write our own.

## The formula

Write the following formula in a file called `clj-zprint.rb`:

```Ruby
class CljZprint < Formula
  desc "Formatter for Clojure"
  homepage "https://github.com/kkinnear/zprint"
  version "2020.08.09"

  if OS.linux?
    url "https://github.com/kkinnear/zprint/releases/download/1.0.0/zprintl-1.0.0"
    sha256 "b707f1188c175c2028c014f0ae88cb384283aa6d097bb31298d66852162581b1"
  else
    url "https://github.com/kkinnear/zprint/releases/download/1.0.0/zprintm-1.0.0"
    sha256 "b707f1188c175c2028c014f0ae88cb384283aa6d097bb31298d66852162581b1"
  end

  def install
    system "mv zprintm-1.0.0 clj-zprint"
    system "chmod 755 clj-zprint"
    bin.install "clj-zprint"
  end

  test do
    system "#{bin}/clj-zprint"
  end
end
```

We create a class called `CljZprint` that extends the `Formula` class. We add some meta data: description, homepage and version. We specify which binary to download based on the OS the formula is running on and how it should be installed.
Finally we test that the executable has been installed correctly by running it.

## Install the package

To install the package we run the formula with the following command (in the directory that contains the formula):

```bash
brew install --build-from-source clj-zprint.rb
```

You can now call `clj-zprint` from the shell/terminal.

## Uninstall the package

To uninstall the package we can run:

```bash
brew uninstall clj-zprint
```

The full example project can be found
[here](https://github.com/andersmurphy/clj-cookbook/blob/master/brew-formulae/README.md).
