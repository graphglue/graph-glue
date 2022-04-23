export function setup() {
    document.querySelectorAll("div[data-platform-hinted]")
        .forEach(elem => elem.addEventListener('click', (event) => togglePlatformDependent(event, elem)))
    document.querySelectorAll("div[tabs-section]")
        .forEach(elem => elem.addEventListener('click', (event) => toggleSectionsEventHandler(event)))
    initTabs()
}

function initTabs() {
    document.querySelectorAll("div[tabs-section]")
        .forEach(element => {
            showCorrespondingTabBody(element)
            element.addEventListener('click', (event) => toggleSectionsEventHandler(event))
        })
    let cached = localStorage.getItem("active-tab")
    if (cached) {
        let parsed = JSON.parse(cached)
        let tab = document.querySelector('div[tabs-section] > button[data-togglable="' + parsed + '"]')
        if (tab) {
            toggleSections(tab)
        }
    }
}

function showCorrespondingTabBody(element) {
    const buttonWithKey = element.querySelector("button[data-active]")
    if (buttonWithKey) {
        const key = buttonWithKey.getAttribute("data-togglable")
        document.querySelector(".tabs-section-body")
            .querySelector("div[data-togglable='" + key + "']")
            .setAttribute("data-active", "")
    }
}

function toggleSections(target) {
    localStorage.setItem('active-tab', JSON.stringify(target.getAttribute("data-togglable")))
    const activateTabs = (containerClass) => {
        for (const element of document.getElementsByClassName(containerClass)) {
            for (const child of element.children) {
                if (child.getAttribute("data-togglable") === target.getAttribute("data-togglable")) {
                    child.setAttribute("data-active", "")
                } else {
                    child.removeAttribute("data-active")
                }
            }
        }
    }

    activateTabs("tabs-section")
    activateTabs("tabs-section-body")
}

function toggleSectionsEventHandler(evt) {
    if (!evt.target.getAttribute("data-togglable")) return
    toggleSections(evt.target)
}

function togglePlatformDependent(e, container) {
    let target = e.target
    if (target.tagName != 'BUTTON') return;
    let index = target.getAttribute('data-toggle')

    for (let child of container.children) {
        if (child.hasAttribute('data-toggle-list')) {
            for (let bm of child.children) {
                if (bm == target) {
                    bm.setAttribute('data-active', "")
                } else if (bm != target) {
                    bm.removeAttribute('data-active')
                }
            }
        } else if (child.getAttribute('data-togglable') == index) {
            child.setAttribute('data-active', "")
        } else {
            child.removeAttribute('data-active')
        }
    }
}