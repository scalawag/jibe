//======================================================================================================================
// Jibe us used to maintain the page-wide state of the jibe app.

var Jibe = new function() {
  var jibeLogs = {};
  var jibeMandates = {};

  //====================================================================================================================
  // The Mandate class is used keep track of one mandate job summary, which maps to a div with a summary and
  // collapsible details in the DOM.

  var Mandate = function(status) {
    this.mandateId = status.id;
    this.elements = this.createElements();
    this.updateStatus(status);

    jibeMandates[this.mandateId] = this;
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

    var icon = null;
    switch ( status.executiveStatus ) {
      case 'PENDING' : icon = 'fa-clock-o'      ; break;
      case 'RUNNING' : icon = 'fa-gear fa-spin' ; break;
      case 'UNNEEDED': icon = 'fa-times'        ; break;
      case 'FAILURE' : icon = 'fa-exclamation'  ; break;
      case 'SUCCESS' : icon = 'fa-check'        ; break;
      case 'NEEDED'  : icon = 'fa-check'        ; break;
      case 'BLOCKED' : icon = 'fa-ban'          ; break;
    }

    this.elements.icon.attr('class', 'fa ' + icon);

    this.elements.description.text(status.description);

    if ( status.endTime && status.startTime ) {
      this.elements.time.text( ( status.endTime - status.startTime ) + ' ms' );
      this.elements.time.attr('title', status.startTime + ' - ' + status.endTime );
    }

    this.elements.mandate.attr('class', 'mandate ' + status.executiveStatus);
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

  var Log = function(mandateId) {
    this.mandateId = mandateId;
//    this.tailStack = [div];
//    this.nextCallSerial = 0;
    this.lastStackTraceSerial = -1;
    this.lastCommandSerial = -1;
//    this.lastTag = undefined;
//    this.inStackTraceLocation = false;
//    this.currentLineNumber = 1;

    this.parseLine = function(line) {
      var parts = line.split('|');
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

    jibeLogs[mandateId] = this;

    this.lastItem = undefined;

    this.elements = {
      root: row,
      lines: logDiv
    }
  }

  Log.prototype.appendLine = function(rawLine) {
    // skip blank lines (ones without even a timestamp) - usually the last one in a block of text
    if ( rawLine.length == 0 ) return;

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
  }

  Log.prototype.appendText = function(text) {
    var self = this;
    var lines = text.split('\n');
    $.each(lines, function(n, line) { self.appendLine(line) });
  }


  //======================================================================================================================


//  function initializeLog(container, mandateId) {
//    var logDiv =
//      $('<div>', {
//        class: 'log',
//        id: mandateId + '_L',
//      });
//
//    var row =
//      $('<div>', {
//        class: 'row mono'
//      }).append(logDiv);
//
//    container.append(row);
//
//    jibeLogs[mandateId] = new Log(logDiv, mandateId);
//
//  }

  function populateChildMandates(container, mandateId) {
    $.getJSON( mandateId + '/children', function( children ) {
      $.each(children, function(n, child) {
        var m = new Mandate(child);

        var details = m.elements.details;

        // recursively add the children of this mandate (if it's a composite).  Add its log otherwise.
        if ( child.composite ) {
          populateChildMandates(details, child.id);
        } else {
          var log = new Log(child.id);
          details.append(log.elements.root);

          // TODO: eventually have the logs load just-in-time when the leaf mandate is expanded instead of all at once.
          $.get( child.id + '/log', function( text ) {
            log.appendText(text);
          });

        }

        container.append(m.elements.mandate);
      })
    })
  }

  // Builds the mandate structure (which doesn't change between runs)
  function initializeMandateStructure(run) {
    $('#runId').text(run.startTime);

    var mandates = $('#mandates');
    mandates.empty();
    populateChildMandates(mandates, 'm0'); // TODO: load children just-in-time when the mandate is expanded.
  }

  var refreshInterval = undefined;

  function refreshMandates() {
    var activeMandateCount = 0;

    $.each(jibeMandates, function(mandateId, mandate) {
      // Only update the ones that could change (pending and running).  Every other status is terminal.

      if ( mandate.status.executiveStatus == 'PENDING' || mandate.status.executiveStatus == 'RUNNING' ) {
        console.log('poll ' + mandateId);
        $.getJSON( mandateId + '/status', function( status ) {
          if ( status != null ) mandate.updateStatus(status);
        });

        activeMandateCount += 1;
      }

    });

    // Stop polling once all of our mandates have reached a terminal state.

    if ( activeMandateCount == 0 )
      clearInterval(refreshInterval);
  }

  this.initialize = function() {
    $.getJSON( "run", function( run ) {
      initializeMandateStructure(run);
      refreshInterval = setInterval(refreshMandates, 1000);
    })
  }
};
