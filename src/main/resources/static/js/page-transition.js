(function () {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  if ("startViewTransition" in document) return;

  var leaving = false;

  document.addEventListener("click", function (event) {
    if (leaving || event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

    var target = event.target;
    if (!target || target.nodeType !== 1) target = target && target.parentElement;
    if (!target || !target.closest) return;

    var link = target.closest("a");
    if (!link) return;
    if (!link.closest("header .nav") && !link.matches("a.brand")) return;
    if (link.hasAttribute("download")) return;
    if (link.target && link.target.toLowerCase() !== "_self") return;

    var method = (link.getAttribute("method") || "get").toLowerCase();
    if (method !== "get") return;

    var raw = link.getAttribute("href");
    if (!raw || raw.charAt(0) === "#") return;

    var url;
    try {
      url = new URL(link.href, window.location.href);
    } catch (e) {
      return;
    }
    if (url.origin !== window.location.origin) return;
    if (url.protocol !== "http:" && url.protocol !== "https:") return;
    if (
      url.pathname === window.location.pathname &&
      url.search === window.location.search &&
      url.hash
    ) {
      return;
    }

    var main = document.querySelector("main");
    if (!main) return;

    event.preventDefault();
    leaving = true;
    main.classList.add("is-leaving");
    window.setTimeout(function () {
      window.location.assign(url.href);
    }, 180);
  });

  window.addEventListener("pageshow", function () {
    leaving = false;
    var main = document.querySelector("main");
    if (main) main.classList.remove("is-leaving");
  });
})();
