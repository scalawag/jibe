//======================================================================================================================
// Jibe us used to maintain the page-wide state of the jibe app.

var Jibe = new function() {
  //====================================================================================================================
  // The Mandate class is used keep track of one mandate job summary, which maps to a div with a summary and
  // collapsible details in the DOM.

  var Mandate = function(runId, status) {
    this.runId = runId;
    this.mandateId = status.id;
    this.elements = this.createElements();

    // If this is a leaf mandate, initialize its log.
    if ( ! status.composite ) {
      // TODO: eventually have the logs load just-in-time when the leaf mandate is expanded instead of all at once?
      this.log = new Log(runId, status.id);
      this.elements.details.append(this.log.elements.root);
      this.log.update();
    }

    // initialize the status
    this.updateStatus(status);

    // start polling for status changes
    var self = this;
    this.updateInterval = setInterval(function() { self.update() }, 1000);
  }

  // Create the summary and details structure (which doesn't change throughout the life of this Mandate.  Only the
  // strings and classes will change from now on.  This intentionally does not have access to the status because I
  // want to make sure that the updateSummary() is used to redraw everything when a new status is passed in.

  Mandate.prototype.createElements = function() {
    var self = this;

    var descSpan =
      $('<span>', {
        'class': 'force-content'
      });

    var collapserDiv =
      $('<div>', {
        'class': 'box collapser',
        'shutter-indicator': self.mandateId,
      }).append($('<i class="fa fa-caret-right"></i>'));

    var iconSpan = $('<i class="fa fa-dot-circle-o"></i>');

    var iconDiv =
      $('<div>', {
        'class': 'box icon',
      }).append(iconSpan);

    var timeDiv =
      $('<div>', {
        'class': 'box time',
      });

    var descriptionDiv =
      $('<div>', {
        'class': 'box description',
      }).append(descSpan);

    var summaryDiv = $('<div>', {
      'class': 'row summary',
    })
    .append(collapserDiv, iconDiv, timeDiv, descriptionDiv)
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(self.mandateId);
    });

    var shutterDiv = $('<div>', {
      class: 'indent details',
      shutter: self.mandateId,
      shuttered: 'true'
    }).css('display', 'none');

    var mandateDiv = $('<div>', {
      id: self.mandateId,
      class: 'mandate'
    }).append(summaryDiv, shutterDiv);

    // Elements that we'll need to reference later
    return {
      description: descSpan,
      time: timeDiv,
      icon: iconSpan,
      summary: summaryDiv,
      mandate: mandateDiv,
      details: shutterDiv,
    };
  }

  Mandate.prototype.updateStatus = function(status) {
    this.status = status;

    var icon = Jibe.getIconClassForStatus(status.executiveStatus);

    this.elements.icon.attr('class', 'fa ' + icon);

    this.elements.description.text(status.description);

    if ( status.endTime && status.startTime ) {
      this.elements.time.text( ( status.endTime - status.startTime ) + ' ms' );
      this.elements.time.attr('title', status.startTime + ' - ' + status.endTime );
    }

    this.elements.mandate.attr('class', 'mandate ' + status.executiveStatus);
  }

  Mandate.prototype.update = function() {
    var self = this;

    console.log('updating mandate' + self.status.id);

    $.getJSON( '/data/run/' + self.runId + '/' + self.status.id + '/status', function( status ) {
      if ( status != null ) self.updateStatus(status);
    });

    if ( self.log ) self.log.update();

    // Poll again if this mandate has not reached a terminal state.

    if ( self.status.executiveStatus != 'PENDING' && self.status.executiveStatus != 'RUNNING' )
      clearInterval(self.updateInterval);
  }

  //====================================================================================================================
  // Manages collapsible exception stack traces in the mandate logs.

  var ExceptionStackTrace = function(id) {
    this.id = id;
    this.hasStackFrames = false;

    // Create the DOM structure for a stack trace

    var messageDiv = $('<div>', {
      'class': 'message',
      'shutter-control': id,
    })
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(id);
    });

    var stackFrameDiv = $('<div>', {
      'class': 'location',
      'shutter': id,
      'shuttered': 'true',
    }).css('display', 'none');

    var insert = $('<div>', {
      'class': 'collapser-insert',
    }).append(messageDiv, stackFrameDiv);

    var collapser = $('<div>', {
      'class': 'collapser',
       'shutter-control': id,
       'shutter-indicator': id,
    })
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(id);
    })
    .append($('<span class="fa fa-caret-right"></span>'));

    var stackTraceDiv = $('<div>', {
      class: 'stack-trace',
      id: id,
    }).append(collapser, insert);

    this.elements = {
      root: stackTraceDiv,
      messages: messageDiv,
      frames: stackFrameDiv,
    };

  }

  ExceptionStackTrace.prototype.classify = function(line) {
    if ( line.text.startsWith('\tat ') || line.text.startsWith('\t...') ) {
      line.classification = 'stack-frame';
    } else if ( line.text.startsWith('Caused by: ') ) {
      // This could eventually be another classification 'cause' if we want to nest them within the primary.
      line.classification = 'message';
    } else {
      line.classification = 'message';
    }
    return line;
  }

  ExceptionStackTrace.prototype.createLineDiv = function(line) {
    return $('<div>', {
      class: line.classification + ' line ' + line.level,
      title: line.timestamp,
    }).text(line.text);
  }

  // Returns true if the message was appended and false if it could not be (usually because it's a 'message' type
  // message and this object already contains stack frames).

  ExceptionStackTrace.prototype.append = function(line) {
    line = this.classify(line)
    switch (line.classification) {
      case 'stack-frame':
        if (! this.hasStackFrames) {
          this.hasStackFrames = true;
        }
        this.elements.frames.append(this.createLineDiv(line))
        return true;

      case 'message':
        if (this.hasStackFrames) {
          return false;
        } else {
          this.elements.messages.append(this.createLineDiv(line))
          return true;
        }
    }
  }

  //====================================================================================================================
  // Manages collapsible method calls in the mandate logs.

  var MethodCall = function() {
  }

  //====================================================================================================================

  var CommandExecution = function(id) {
    this.id = id;

    commandDiv = $('<div>', {
      class: 'command',
      id: id,
    });

    this.elements = {
      root: commandDiv,
    };
  }

  CommandExecution.prototype.appendHeader = function(line) {
    var self = this;

    var header = $('<div>', {
      'class': 'line',
      'shutter-control': self.id,
    }).text('Command: ' + line.text);

    var messageDiv = $('<div>', {
      'class': 'section start',
      'shutter-control': self.id,
    })
    .append(header)
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(self.id);
    });

    var lineNumbers = $('<div>', {
      'class': 'line-numbers'
    });

    var contentDiv = $('<div>', {
      'class': 'section content',
      'shutter': self.id,
      'shuttered': 'true',
    })
    .css('display', 'none')
    .append(lineNumbers);

    var insert = $('<div>', {
      'class': 'collapser-insert',
    })
    .append(messageDiv, contentDiv);

    var collapser = $('<div>', {
      'class': 'collapser',
      'shutter-control': self.id,
      'shutter-indicator': self.id,
    })
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(self.id);
    })
    .append($('<span class="fa fa-caret-right"></span>'));

    this.elements.root.append(collapser, insert);

    this.elements.header = header;
    this.elements.sections = insert;
    this.elements.scriptContent = contentDiv;
    this.elements.lineNumbers = lineNumbers;
    this.currentLineNumber = 1;
  }

  CommandExecution.prototype.appendContent = function(line) {
    this.elements.lineNumbers.append($('<div class="line-number"/>').text(this.currentLineNumber + ':'));

    var lspan = $('<span class="force-content">').text(line.text);
    var ldiv = $('<div class="line" title="' + line.timestamp + '"/>').append(lspan);
    this.elements.scriptContent.append(ldiv);
    hljs.highlightBlock(ldiv[0]);

    this.currentLineNumber += 1;
  }

  CommandExecution.prototype.appendOutput = function(line) {

    // Create the output section if it's not already there
    if ( this.elements.scriptOutput == undefined ) {
      this.elements.scriptOutput = $('<div>', {
        'class': 'section output'
      });
      this.elements.sections.append(this.elements.scriptOutput);
    }

    var lspan = $('<span class="force-content line"/>').text(line.text);
    var ldiv = $('<div>', {
     class: 'line ' + line.level,
     title: ''+ line.timestamp,
    }).append(lspan);

    this.elements.scriptOutput.append(ldiv);
  }

  CommandExecution.prototype.appendFooter = function(line) {

    // Create the exit section if it's not already there
    if ( this.elements.exitSection == undefined ) {
      this.elements.exitSection = $('<div>', {
        'class': 'section exit'
      });
      this.elements.sections.append(this.elements.exitSection);
    }

    var ldiv = $('<div>', {
     class: 'line ' + line.level,
     title: ''+ line.timestamp,
    })
    .css('cursor', 'pointer')
    .click(function() {
      shutterToggle(self.id);
    })
    .text('Exit Code = ' + line.text);

    this.elements.exitSection.append(ldiv);
  }

  CommandExecution.prototype.append = function(line) {
    switch (line.tag) {
      case 'CS': this.appendHeader(line);  break;
      case 'CC': this.appendContent(line); break;
      case 'CO': this.appendOutput(line);  break;
      case 'CE': this.appendFooter(line);  break;
    }
  }

  //====================================================================================================================
  // Log class is used to maintain the log for a single leaf mandate.  Its main goal is to make it so that it can
  // build up the log display one line at a time. This makes it possible to append to the log without having to
  // reprocess the whole thing from scratch whenever new lines are written.

  var Log = function(runId, mandateId) {
    this.runId = runId;
    this.mandateId = mandateId;
    this.bytesRendered = 0;
    this.lastStackTraceSerial = -1;
    this.lastCommandSerial = -1;

    this.parseLine = function(line) {
      var parts = line.split('|');

      // Handle |s in the actual text of the log line by replacing them.
      if ( parts.length > 4 ) {
        parts[3] = parts.slice(3).join('|');
      }

      return {
        tag: parts[0],
        level: parts[1],
        timestamp: parts[2],
        text: parts[3]
      }
    }

    var logDiv =
      $('<div>', {
        class: 'log',
        id: mandateId + '_L',
      });

    var row =
      $('<div>', {
        class: 'row mono'
      }).append(logDiv);

    this.div = logDiv;

    this.lastItem = undefined;

    this.elements = {
      root: row,
      lines: logDiv
    }
  }

  Log.prototype.appendLine = function(rawLine) {
    var line = this.parseLine(rawLine);

    switch (line.tag) {

      case 'EE':

        // Exception line.  We need to create a new ExceptionStackTrace if we can't append it to (possible) one that's
        // already at the end of the log.

        if ( this.lastItem == undefined || ! this.lastItem instanceof ExceptionStackTrace || ! this.lastItem.append(line) ) {
          // The lastItem (if it's there) can't take this line.  Add new EST and append this line to it.

          this.lastStackTraceSerial += 1;
          this.lastItem = new ExceptionStackTrace(this.mandateId + '_L_E_' + this.lastStackTraceSerial);
          this.div.append(this.lastItem.elements.root);
          this.lastItem.append(line);
        }

        break;

      case 'CS':
        this.lastCommandSerial += 1;
        this.lastItem = new CommandExecution(this.mandateId + '_L_C_' + this.lastCommandSerial);
        this.div.append(this.lastItem.elements.root);

        // fall through and append this line.

      case 'CC':
      case 'CO':
      case 'CE':
        // We better have seen the CS that makes this.lastItem a CommandExecution that can handle these.
        this.lastItem.append(line);
        break;


  //    case 'FS':
  //      <div class="call">
  //        <div class="collapser" shutter-control={sid} shutter-indicator={sid}><span class="fa fa-caret-right"></span></div>
  //        <div class="collapser-insert">
  //          <div class="section start">
  //            <div class="line" shutter-control={sid}>Command: {cmd.name.text}</div> <!-- TODO: timestamp -->
  //          </div>
  //          <div class="section content" shutter={sid} shuttered="true">
  //
  //      var e =
  //        $('<div>', {
  //          class: level + ' line ' + tag,
  //          title: timestamp,
  //        }).text('Mandate Call: ' + text);
  //
  //      this.appendElement(e);
  //      break;
  //
  //    case 'FR':
  //
  //      var e =
  //        $('<div>', {
  //          class: level + ' line ' + tag,
  //          title: timestamp,
  //        }).text('Returned: ' + text);
  //
  //      this.appendElement(e);
  //      break;

      default:
        var e =
          $('<div>', {
            class: line.level + ' line ' + line.tag,
            title: line.timestamp,
          }).text(line.text);

        this.elements.lines.append(e);
    }

    this.bytesRendered += rawLine.length + 1;
  }

  Log.prototype.appendText = function(text) {
    var self = this;
    var lines = text.split('\n');

    // Always skip the last line.  It could be partial (and we don't know how to render partial lines) and the
    // last line, once the log is complete, will always be empty.
    lines.pop();

    $.each(lines, function(n, line) {
      self.appendLine(line);
    });
  }

  Log.prototype.update = function() {
    var self = this;
    console.log('updating log: ' + self.mandateId + ' from ' + self.bytesRendered + ' bytes');

    $.ajax({
      url: '/data/run/' + self.runId + '/' + self.mandateId + '/log',
      type: 'GET',
      dataType: 'json',
      headers: { 'Range': 'bytes=' + self.bytesRendered + '-' },
      complete: function ( rsp ) {
        if ( rsp.status == 206 )
          self.appendText(rsp.responseText);
      },
    });
  }

  //======================================================================================================================

  function populateChildMandates(container, runId, mandateId) {
    $.getJSON( '/data/run/' + runId + '/' + mandateId + '/children', function( children ) {
      $.each(children, function(n, child) {
        var m = new Mandate(runId, child);

        var details = m.elements.details;

        // recursively add the children of this mandate (if it's a composite).  Add its log otherwise.
        if ( child.composite ) {
          populateChildMandates(details, runId, child.id);
        }

        container.append(m.elements.mandate);
      })
    })
  }

  // Builds the mandate structure (which doesn't change between runs)
  function initializeMandateStructure(runId, run) {
    $('#runId').text(run.startTime);

    var mandates = $('#mandates');
    mandates.empty();
    populateChildMandates(mandates, runId, 'm0'); // TODO: load children just-in-time when the mandate is expanded.
  }

  function initializeRun(runId) {
    $.getJSON('/data/run/' + runId + '/run', function( run ) {
      initializeMandateStructure(runId, run);
    })
  }

  this.initialize = function() {
    var pageId = document.location.href;
    pageId = pageId.replace(/\/$/,'').replace(/.*\//,'');

    if ( pageId == 'latest' ) {
      $.getJSON('/data/runs?limit=1', function( run ) {
        initializeRun(run[0].description);
      })
    } else {
      initializeRun(pageId);
    }
  }

  this.getIconClassForStatus = function(status) {
    switch ( status ) {
      case 'PENDING' : return 'fa-clock-o';
      case 'RUNNING' : return 'fa-gear fa-spin';
      case 'UNNEEDED': return 'fa-times';
      case 'FAILURE' : return 'fa-exclamation';
      case 'SUCCESS' : return 'fa-check';
      case 'NEEDED'  : return 'fa-check';
      case 'BLOCKED' : return 'fa-ban';
    }
  }

  this.updateRunList = function(limit, offset) {
    limit = limit || 20
    offset = offset || 0

    var now = moment();

    var params = $.param({
      limit: limit,
      offset: offset,
    });

    function df(m) {
      return m.format('MM/DD/YYYY hh:mm:ss a');
    }

/*
    function pad(n, width) {
      var add = width - Math.floor(Math.log10(n)) - 1;
      return "0".repeat(add) + n;
    }

    function age2(ms) {
      var w, d, h, m, s, f;
      s = Math.floor(ms / 1000);
      f = ms % 1000;
      m = Math.floor(s / 60);
      s = s % 60;
      h = Math.floor(m / 60);
      m = m % 60;
      d = Math.floor(h / 24);
      h = h % 24;
      w = Math.floor(d / 7);
      d = d % 7;


      if ( w > 0 || d > 0 ) {
        var s = moment(now - ms);
        return s.fromNow(true);
      } else {
        var out = '';
        if ( h > 0 ) out += pad(h,2) + ':';
        if ( m > 0 ) out += pad(m,2) + ':';
        out += pad(s,2) + '.' + pad(f,3);
        return out;
      }
    }
*/

    function age(ms) {
      var w, d, h, m, s, f;
      s = Math.floor(ms / 1000);
      f = ms % 1000;
      m = Math.floor(s / 60);
      s = s % 60;
      h = Math.floor(m / 60);
      m = m % 60;
      d = Math.floor(h / 24);
      h = h % 24;
      w = Math.floor(d / 7);
      d = d % 7;

      var out = '';

      if ( w > 0 )
        out += w + " w";
      if ( d > 0 )
        out += ' ' + d + 'd';
      if ( h > 0 )
        out += ' ' + h + 'h';
      if ( m > 0 )
        out += ' ' + m + 'm';
      if ( s > 0 )
        out += ' ' + s + 's';
      if ( f > 0 )
        out += ' ' + f + 'ms';

      return out;
    }

    $.getJSON('/data/runs?' + params, function( data ) {
      var runs = $("table#runs");

      $.each(data, function(n, run) {
        var dateParts = run.description.split('-');
        var date = new Date(dateParts[0],dateParts[1] - 1,dateParts[2],dateParts[3],dateParts[4],dateParts[5],dateParts[6]);
        var timestamp = moment(date);

        var icon = $('<i>', {
          class: 'fa ' + Jibe.getIconClassForStatus(run.executiveStatus)
        });

        var runtime = $('<span>');

        if ( run.endTime && run.startTime ) {
          var sm = moment(run.startTime);
          var em = moment(run.endTime);
          runtime.text(age(em.diff(sm)));
          runtime.attr('title', df(sm) + ' - ' + df(em) );
        } else if ( run.startTime ) {
          var sm = moment(run.startTime);
          runtime.text(age(now.diff(sm)));
          runtime.attr('title', df(sm) + ' - ');
        }

        var trash = $('<i>', {
          class: 'fa fa-trash-o'
        })
        .click(function() {
          if ( confirm('Do you really want to delete the results from run "' + run.description + '"?') )
            alert("You can't actually do that yet.");
        });

        function link() {
          return $('<a>', { href: '/' + run.description + '/' });
        }

        var e =
          $('<tr>', {
            class: 'mandate ' + run.executiveStatus,
          }).append([
            $('<td class="icon">').append(link().append(icon)),
            $('<td>').append(link().text(df(timestamp))),
            $('<td>').append(link().text(timestamp.calendar())),
            $('<td>').append(link().text(timestamp.fromNow())),
            $('<td class="runtime">').append(link().append(runtime)),
            $('<td>').append(trash),
          ])

        e.appendTo( runs );
      });
    });
  }

};
