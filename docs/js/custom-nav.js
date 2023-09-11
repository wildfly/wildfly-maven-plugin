/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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