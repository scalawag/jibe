
function toggleHide(elt) {
  var control = $(elt);
  var outcome = control.attr("outcome");
  var mandates = $("body").find(".mandate." + outcome);

  if ( control.hasClass("toggle-on") ) {
    control.removeClass("toggle-on").addClass("toggle-off")
    control.attr("title", control.attr("title").replace("Hide","Show"))

    mandates.addClass("hidden")
  } else {
    control.removeClass("toggle-off").addClass("toggle-on")
    control.attr("title", control.attr("title").replace("Show","Hide"))

    mandates.removeClass("hidden")
  }
}

function shutterOpen(elt) {
  var id = elt.attr("shutter");
  var indicators = $("body").find("[shutter-indicator='" + id + "']");

  elt.slideDown(200);
  indicators.addClass("spindown").removeClass("spinup");
  elt.attr("shuttered", "false");
}

function shutterClose(elt) {
  var id = elt.attr("shutter");
  var indicators = $("body").find("[shutter-indicator='" + id + "']");

  elt.slideUp(200);
  indicators.addClass("spinup").removeClass("spindown");
  elt.attr("shuttered", "true");
}

function shutterOpenAll() {
  var targets = $("body").find("[shutter]");
  targets.each(function() {
    shutterOpen($(this));
  });
}

function shutterOpenMandates() {
  var toOpen = $("body").find("[shutter]");
  toOpen.each(function() {
    var _this = $(this);
    if ( _this.children(".mandate").length > 0 ) {
      shutterOpen(_this);
    } else {
      shutterClose(_this);
    }
  });
}

function shutterCloseAll() {
  var targets = $("body").find("[shutter]");
  targets.each(function() {
    shutterClose($(this));
  });
}

function shutterToggle(id) {
  var targets = $("body").find("[shutter='" + id + "']");
  var indicators = $("body").find("[shutter-indicator='" + id + "']");
  var target = $(targets[0]);

  if ( target.attr("shuttered") == "false" ) {
    target.slideUp(200);
    indicators.addClass("spinup").removeClass("spindown");
    target.attr("shuttered", "true");
  } else {
    target.slideDown(200);
    indicators.addClass("spindown").removeClass("spinup");
    target.attr("shuttered", "false");
  }
}

$(function() {
  var nodes = $("body").find("[shutter-control]");
  nodes.css("cursor", "pointer");
  nodes.click(function() {
    var tgt = $(this).attr("shutter-control");
    shutterToggle(tgt);
  });

  var shuttered = $("body").find("[shuttered='true']");
  shuttered.css("display","none");

  // syntax highlighting for command script content
  $("body").find("div.command div.content div.line").each(function() {
    hljs.highlightBlock(this);
  });
});
