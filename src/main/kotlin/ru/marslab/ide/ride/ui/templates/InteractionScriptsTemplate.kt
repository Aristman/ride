package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для JavaScript кода интерактивности элементов
 */
class InteractionScriptsTemplate : BaseHtmlTemplate() {

    fun createScripts(): String {
        return """
            <script>
            function copyCodeBlock(button) {
                const container = button.closest('.code-block-container');
                const codeElement = container.querySelector('.code-content code');
                const text = codeElement.textContent;

                navigator.clipboard.writeText(text).then(() => {
                    const originalText = button.querySelector('.copy-text').textContent;
                    button.querySelector('.copy-text').textContent = 'Copied!';
                    button.style.background = 'var(--success-color, #4ade80)';

                    setTimeout(() => {
                        button.querySelector('.copy-text').textContent = originalText;
                        button.style.background = '';
                    }, 2000);
                }).catch(err => {
                    console.error('Failed to copy text: ', err);
                });
            }

            function toggleStructured(button) {
                const container = button.closest('.structured-block');
                const content = container.querySelector('.structured-content');
                const arrow = button.querySelector('.toggle-structured');

                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    arrow.textContent = '▼';
                } else {
                    content.style.display = 'none';
                    arrow.textContent = '▶';
                }
            }

            function toggleMetadata(button) {
                const container = button.closest('.tool-metadata');
                const content = container.querySelector('.metadata-content');
                const arrow = button.querySelector('.metadata-arrow');

                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    arrow.textContent = '▼';
                } else {
                    content.style.display = 'none';
                    arrow.textContent = '▶';
                }
            }

            function viewFullContent(button) {
                const container = button.closest('.tool-code-content');
                const preview = container.querySelector('.code-preview-body');
                const fullContent = container.querySelector('.full-code-content');

                if (fullContent) {
                    preview.style.display = 'none';
                    fullContent.style.display = 'block';
                    button.textContent = 'Hide';
                } else {
                    preview.style.display = 'block';
                    button.textContent = 'View Full';
                }
            }
            </script>
        """.trimIndent()
    }

    override fun render(variables: Map<String, Any>): String {
        return createScripts()
    }
}