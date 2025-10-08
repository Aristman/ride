// Функция для удаления элемента с fade-out анимацией
window.removeElementWithFade = function(selector) {
    const elements = document.querySelectorAll(selector);
    elements.forEach(el => {
        el.classList.add('fade-out');
        setTimeout(() => {
            el.remove();
        }, 300); // Длительность анимации
    });
};

document.addEventListener('click', (e) => {
    const a = e.target.closest('a.code-copy-link');
    if (!a) return;
    e.preventDefault();
    const table = a.closest('table.code-block');
    const code = table && table.querySelector('pre code');
    if (code) {
        try {
            navigator.clipboard.writeText(code.textContent || '');
        } catch (_) {}
    }
});

// Lazy load highlight.js once
window.__ride_initHl = function() {
    if (window.__ride_hlLoaded) return;
    console.log('DEBUG: Initializing highlight.js');

    const addCss = (href) => {
        console.log('DEBUG: Adding CSS', href);
        const l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = href;
        document.head.appendChild(l);
    };

    const addJs = (src, onload) => {
        console.log('DEBUG: Adding JS', src);
        const s = document.createElement('script');
        s.src = src;
        s.onload = onload;
        document.head.appendChild(s);
    };

    addCss('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css');
    addJs('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js', function() {
        console.log('DEBUG: highlight.js loaded successfully');
        window.__ride_hlLoaded = true;
        window.__ride_highlightAll();
    });
};

window.__ride_highlightAll = function() {
    if (!window.hljs) return;
    document.querySelectorAll('pre code').forEach(function(block) {
        try {
            window.hljs.highlightElement(block);
        } catch (_) {}
    });
};