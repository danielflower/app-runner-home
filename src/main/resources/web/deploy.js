document.addEventListener("DOMContentLoaded", function (event) {
    if (!("fetch" in window)) {
        return;
    }
    var f = document.querySelector('.deploy-form');
    var button = f.querySelector('input');
    var url = f.getAttribute("action");
    var output = f.querySelector('.output');
    f.onsubmit = function () {
        while (output.firstChild) output.removeChild(output.firstChild);
        output.appendChild(document.createTextNode('Building....\n'));
        window.fetch(url, {method: 'post'})
            .then(function (res) {
                return pump(res.body.getReader());
            });
        button.setAttribute('disabled', 'disabled');
        return false;
    };


    var decoder = new TextDecoder();
    function pump(reader) {
        return reader.read().then(function (result) {
            if (result.done) {
                button.removeAttribute('disabled');
                return;
            }
            var chunk = decoder.decode(result.value || new Uint8Array, {
                stream: !result.done
            });
            output.appendChild(document.createTextNode(chunk));

            output.scrollTop = output.scrollHeight;

            return pump(reader);
        });
    }
});
