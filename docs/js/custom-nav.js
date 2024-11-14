/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
(() => {
    "use strict";

    window.addEventListener("load", () => {
        const navList = document.querySelector("ul[aria-labelledby='Releases_menu']");
        // Clear the current navigation
        while (navList.firstChild) {
            navList.removeChild(navList.firstChild);
        }
        // Create the map of current releases
        const versions = new Map([
            ["5.1", "(alpha)"],
            ["5.0", "(stable)"],
            ["4.2", "(stable)"],
            ["4.1", "(stable)"]
        ]);
        // Add navigation links for other versions
        for (let [key, value] of versions) {
            const li = document.createElement("li");
            const a = document.createElement("a");
            li.appendChild(a);
            a.classList.add("dropdown-item");
            a.setAttribute("href", `../${key}`);
            a.setAttribute("title", `${key} ${value}`);
            a.setAttribute("aria-lab", `${key} ${value}`);
            a.textContent = `${key} ${value}`;
            navList.appendChild(li);
        }
    });
})()