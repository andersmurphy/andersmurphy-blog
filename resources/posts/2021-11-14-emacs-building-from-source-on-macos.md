Title: Emacs: building from source on macOS

I've always wanted to build Emacs from source as it lets you try new features. Native compilation in particular was something I wanted to explore. Native compilation leverages `libgccjit` to compile Emacs lisp code to native code making Emacs very snappy.

Why not use Homebrew? Homebrew is great and I still use it for installing dependencies like `libgccjit` but recently Homebrew has started removing options on their code formulae which makes it a lot less convenient for messing around with emacs.

Let's get started.

Install dependencies.

```bash
brew install libxml2 gcc libgccjit
```

Clone Emacs 28 from [savannah](https://savannah.gnu.org/git/?group=emacs).  We clone a single branch for a slightly smaller download size.

```bash
git clone https://git.savannah.gnu.org/git/emacs.git --branch emacs-28 --single-branch
```

Configure build:

```bash
cd emacs
git checkout emacs-28
./autogen.sh
./configure --with-cairo --with-imagemagick --with-xwidgets --with-native-compilation
```

Start the build. Worth pointing out building emacs from source does take some time `-j$(nproc)` ensures you are at least using all your system's cores.

```bash
make -j$(nproc)
make clean install
```

Move the executable to Applications folder.

```bash
mv nextSteps/Emacs.app /Applications
```

The only problem I ran into was Emacs not being able to find `libgccjit` this was solved by making sure `exec-path-from-shell` was the first package that loaded by use package in my `init.el`.

With this knowledge you can now build a specific version of Emacs with options you want to try. Liberating.
