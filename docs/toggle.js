const storedTheme = localStorage.getItem('theme') || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")

if (storedTheme)
  document.documentElement.setAttribute('data-theme', storedTheme)

function iconChange(theme) {
  if (theme === "dark") {
    toggle.classList.add("theme-toggle--toggled")
  } else {
    toggle.classList.remove("theme-toggle--toggled")
  }
}

function modeSwitch() {
  const currentTheme = document.documentElement.getAttribute("data-theme")
  const targetTheme = currentTheme === "light"  ? "dark" : "light"
  
  document.documentElement.setAttribute('data-theme', targetTheme)
  localStorage.setItem('theme', targetTheme)
  iconChange(targetTheme)
}

window.addEventListener("load", (event) => {
  const toggle = document.querySelector("#toggle")
  toggle.addEventListener("click", modeSwitch)
  const currentTheme = document.documentElement.getAttribute("data-theme")
  iconChange(currentTheme)
})


