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

// Устанавливает HTML содержимое для всех сообщений
window.__ride_setBody = function(htmlContent) {
    console.log('DEBUG: JCEF setBody called with HTML length:', htmlContent.length);
    const root = document.getElementById('messages');
    root.innerHTML = htmlContent;

    requestAnimationFrame(() => { window.scrollTo(0, document.body.scrollHeight); });
    console.log('DEBUG: Calling __ride_initHl and __ride_highlightAll');
    window.__ride_initHl && window.__ride_initHl();
    window.__ride_highlightAll && window.__ride_highlightAll();
};

// Добавляет HTML содержимое к существующим сообщениям
window.__ride_appendHtml = function(htmlContent) {
    console.log('DEBUG: JCEF appendHtml called with HTML length:', htmlContent.length);
    const tmp = document.createElement('div');
    const htmlStr = htmlContent;
    console.log('DEBUG: HTML string preview:', htmlStr.substring(0, 200));
    tmp.innerHTML = htmlStr;
    console.log('DEBUG: tmp.innerHTML preview:', tmp.innerHTML.substring(0, 200));

    const root = document.getElementById('messages');

    // Запоминаем количество блоков кода до добавления
    const blocksBefore = root.querySelectorAll('pre code').length;

    // Добавляем новые элементы
    while (tmp.firstChild) root.appendChild(tmp.firstChild);

    requestAnimationFrame(() => { window.scrollTo(0, document.body.scrollHeight); });
    console.log('DEBUG: Calling __ride_initHl in appendHtml');
    window.__ride_initHl && window.__ride_initHl();

    // Подсвечиваем все новые блоки кода
    const blocksAfter = root.querySelectorAll('pre code');
    console.log('DEBUG: Code blocks before:', blocksBefore, 'after:', blocksAfter.length);

    if (window.hljs && blocksAfter.length > blocksBefore) {
        console.log('DEBUG: Highlighting', (blocksAfter.length - blocksBefore), 'new code blocks');
        // Подсвечиваем только новые блоки (с индекса blocksBefore до конца)
        for (let i = blocksBefore; i < blocksAfter.length; i++) {
            try {
                console.log('DEBUG: Highlighting block', i);
                window.hljs.highlightElement(blocksAfter[i]);
            } catch(e) {
                console.log('DEBUG: Error highlighting block', i, e);
            }
        }
    }
};

// Функция для переключения отображения результатов tool agent
window.toggleOutput = function(outputId) {
    console.log('🔗 Toggle output clicked:', outputId);

    try {
        const element = document.getElementById(outputId);
        if (!element) {
            console.error('❌ Element not found:', outputId);
            return;
        }

        const icon = element.querySelector('.toggle-icon');
        const content = element.querySelector('.output-content');

        if (!icon) {
            console.error('❌ Toggle icon element not found');
            return;
        }

        if (!content) {
            console.error('❌ Output content element not found');
            return;
        }

        const isExpanded = element.classList.contains('expanded');
        console.log('Current state - Expanded:', isExpanded);

        if (isExpanded) {
            // Сворачиваем
            element.classList.remove('expanded');
            element.classList.add('collapsed');
            icon.textContent = '▶';
            content.style.display = 'none';
            console.log('📁 Collapsed output container');
        } else {
            // Разворачиваем
            element.classList.remove('collapsed');
            element.classList.add('expanded');
            icon.textContent = '▼';
            content.style.display = 'block';
            console.log('📂 Expanded output container');
        }
    } catch (error) {
        console.error('❌ Error toggling output:', error);
    }
};

// Глобальный обработчик кликов для разворачивания результатов tool agents
document.addEventListener('click', function(event) {
    console.log('🖱️ Click detected on element:', event.target.className, event.target.tagName);

    // Проверяем клик по заголовку результата
    const outputHeader = event.target.closest('.output-header');
    if (outputHeader) {
        console.log('✅ Output header found:', outputHeader);
        const outputId = outputHeader.getAttribute('data-output-id');
        console.log('📋 Output ID extracted:', outputId);

        if (outputId && outputId.startsWith('output-')) {
            console.log('🎯 Valid output ID, calling toggleOutput:', outputId);
            event.preventDefault();
            event.stopPropagation();
            if (window.toggleOutput) {
                window.toggleOutput(outputId);
            } else {
                console.error('❌ toggleOutput function not found!');
            }
        } else {
            console.warn('⚠️ Invalid output ID:', outputId);
        }
    }
});