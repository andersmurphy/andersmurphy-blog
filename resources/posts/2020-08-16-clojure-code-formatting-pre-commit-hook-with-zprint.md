Title: Clojure: code formatting pre-commit hook with zprint

As a codebase and the team working on it grows it helps to keep the formatting of a project consistent. This makes the code easier to read and removes the need for unnecessary formatting debates on pull requests. One way to achieve this is to add a formatting check to your continuous integration pipeline. The problem with this solution is that it can add unnecessary overhead. You push a commit, the continuous integration job fails due to incorrect formatting, you fix the formatting issue, and push a new commit. Why can't the formatter just fix the issue itself and save you the trouble? This article will cover setting up [pre-commit](https://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks) to automatically format Clojure code with [zprint](https://github.com/kkinnear/zprint) to achieve a more streamlined workflow.

## What are git hooks?

Git hooks are scripts that Git executes before or after a git action occurs such as: committing or merging. They are a built-in feature that runs locally and can be used to implement all sorts of source control related automation. We can use this hook mechanism to implement automatic code formatting with zprint.

## What is zprint?

[zprint](https://github.com/kkinnear/zprint) is a highly configurable library and command line tool that provides a variety of pretty printing capabilities for both Clojure code. Importantly, for this use case zprint provides pre-built binaries for macOS/linux which do not require Java, and that start up in <50ms. This start up time makes it ideal for running as part of a pre-commit hook. Pre-commit hooks should ideally be fast as the slower the hook, the longer the delay before the developer can start writing their commit message, the worse the user experience, the less likely your team is going to want to use them.

## Download zprint executable

Create a file called `git-hooks/pre-commit` in the root of your project and add the following to it:

```Bash
#!/bin/bash
set -e
mkdir -p .cache
cd .cache

if [ ! -f clj-zprint ]; then
  if [ "$(uname)" == "Darwin" ]; then
    curl -LJO  "https://github.com/kkinnear/zprint/releases/download/1.0.0/zprintm-1.0.0"
    mv zprintm-1.0.0 clj-zprint
    chmod 755 clj-zprint
  elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    curl -LJO "https://github.com/kkinnear/zprint/releases/download/1.0.0/zprintl-1.0.0"
    mv zprintm-1.0.0 clj-zprint
    chmod 755 clj-zprint
  fi
fi

cd ..
    ```

This script runs a curl request to get the zprint binary from github depending on your operating system and caches it. This isn't ideal as the binary won't be shared across projects, but at the time of writing zprint doesn't have a [homebrew](https://brew.sh/) formula (I'll cover rolling our own brew formula in a separate blog post).

Worth noting we've added `set -e` to the top of this script this means it will exit as soon as there is an error. This means it won't run if we fail to download the executable preventing unexpected output.

Update the `.gitignore` so that we don't add the cached executable to the project source control:

```txt
/.cache/
```

## Run zprint

Next we want to add some code to `git-hooks/pre-commit` that runs zprint on each of our staged .clj files:

```Bash
#!/bin/bash
for file in $(git diff --cached --name-only | grep -E '\.(clj)$')
do
  .cache/clj-zprint "{:search-config? true}"  < "$file" > "$file.out"
  mv "$file.out" "$file"
  $(git add "$file")
done
```

## Configure zprint

We can configure zprint by adding a `.zprintrc` configuration file to the root of the project:

```clojure
{:style [:community :justified]
 :map {:comma? false}}
```

## Setup

Configure git to point to the `git-hooks` directory:

```bash
git config core.hooksPath git-hooks
```

Make the script executable:

```bash
chmod +x git-hooks/pre-commit
```

This pre-commit hook will now run before each commit. The first time it runs it will be a bit slower as it will need to download and cache the zprint library.

The full example project can be found for etting up a code formatting pre-commit hook with zprint can be found here
[here](https://github.com/andersmurphy/clj-cookbook/blob/master/README.md).
