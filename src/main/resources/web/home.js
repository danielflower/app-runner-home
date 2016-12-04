'use strict';
document.addEventListener("DOMContentLoaded", function () {
    var searchBox = document.querySelector('.app-filter input');
    if (searchBox) {
        var apps = document.querySelectorAll('.app-item');
        var appsText = document.querySelector('.app-filter .apps-text');
        var count = document.querySelector('.app-filter .app-count');

        // wanna grow... up to be... be a...
        var debouncer = null;

        var normalise = function(val) {
            return (val || '').trim().toLowerCase().replace(/[^a-z0-9]/g, '');
        };

        searchBox.addEventListener('input', function () {
            clearTimeout(debouncer);
            debouncer = setTimeout(function () {
                var value = normalise(searchBox.value);
                var numShown = 0;
                for (var i = 0; i < apps.length; i++) {
                    var match = value === '' || (normalise(apps[i].getAttribute('data-app-name')).indexOf(value) > -1);
                    if (match) numShown++;
                    apps[i].style.display = match ? 'block' : 'none';
                }
                count.textContent = numShown + '';
                appsText.textContent = (numShown === 1) ? 'app' : 'apps';
            }, 100);
        });
    }

});
