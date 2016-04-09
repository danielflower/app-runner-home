var page = require('webpage').create();
var url = '{{url-to-screenshot}}';
var output = '{{output-path}}';
if (url === '{{' /* making it so it defaults to a URL unless the magic string is replaced */ + 'url-to-screenshot}}') url = 'http://localhost:8081/app-runner-home/';
if (output === '{{ ' /* yeah */ + 'output-path}}') output = 'output.png';
var width = 600;
var height = 400;
page.viewportSize = { width: width, height: height };
page.clipRect = { top: 0, left: 0, width: width, height: height };
page.open(url, function() {
    window.setTimeout(function () {
        page.render(output);
        phantom.exit();
    }, 10000); // sleep a bit before taking a screenshot to allow the page to load async stuff
});
