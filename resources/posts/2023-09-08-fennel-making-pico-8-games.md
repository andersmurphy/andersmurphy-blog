Title: Fennel: making PICO-8 games

I recently wrote [a game jam game](https://andersmurphy.itch.io/staglight) for [PICO-8](https://www.lexaloffle.com/pico-8.php) using [Fennel](https://fennel-lang.org). This post goes over setting up a project to write PICO-8 games in Fennel.

PICO-8 is a fantastic game engine for game jams due to its constraints:

```
Display: 128x128, fixed 16 colour palette
Input:   6-button controllers
Carts:   32k data encoded as png files
Sound:   4 channel, 64 definable chip blerps
Code:    P8 Lua (max 8192 tokens of code)
CPU:     4M vm insts/sec
Sprites: Single bank of 128 8x8 sprites (+128 shared)
Map:     128 x 32 Tilemap (+ 128 x 32 shared)
```

These constraints help you avoid over scoping your project which makes you more likely to finish the game in the time constraints of the game jam.

PICO-8 games are written in Lua. Lua is great: tables, first class functions, first class coroutines to name but a few of its awesome features. However, since I mostly write Clojure these days, I've long forgotten how to navigate and edit code without [paredit](http://danmidwood.com/content/2014/11/21/animated-paredit.html). Enter Fennel, a programming language that brings together the speed, simplicity, and reach of Lua with the flexibility of a lisp syntax and macro system.

I'm only going to cover the key parts of getting PICO-8 working with Fennel in this post ([you can find the full example project here](https://github.com/andersmurphy/fennel-pico-8-example)).

## Project structure

The example project is structured as follows. 

```
├── Makefile
├── README.md
├── fennel
├── fnl
│   └── your-game-name.fnl
└── games
    ├── lua
    │   └── your-game-name.lua
    └── your-game-name.p8
```

For the `make` script to run you will need a `fnl/your-game-name.fnl` file and `games/lua/your-game-name.lua`. We also need a `games/your-game-name.p8` file with the following contents:

```
pico-8 cartridge // http://www.pico-8.com
version 41
__lua__
#include lua/your-game-name.lua
```

Where `your-game-name` is the name of your game.

## Symlink

To make it easy for  PICO-8  to access your games `.p8` without having to store the entire project directly in your PICO-8 `carts`  folder we symlink the project's games directory to the PICO-8 carts folder. In my case on OSX this would be:

```
ln -s ~/projects/fennel-pico-8-example/games '~/Library/Application Support/pico-8/carts/games'
```

## Makefile

The last piece of the puzzle is the `Makefile`. Fennel offers ahead-of-time compilation for when the target system of your application does not make it easy to include the Fennel compiler directly but uses Lua. This allows you to compile .fnl files to .lua files that can be included in your PICO-8 `.p8` cart.

```
all: games/lua/*.lua

games/lua/%.lua: fnl/%.fnl
# Compile fennel code to lua
		./fennel/fennel --compile $< > $@
# Remove the module return statement generated by fennel
# (the last line of the output .lua file) as pico-8 doesn't
# support return statements outside of functions.
		sed -i '' '$$d' $@
```

You can then compile your `your-game-name.fnl` file by running `make` at 
the root of the project.

You now have everything you need to make PICO-8 games in Fennel.

The full example [project can be found here](https://github.com/andersmurphy/fennel-pico-8-example).
