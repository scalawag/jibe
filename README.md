Things to prove/demonstrate:
 - command timeouts
 - file transfer
 - templating
 - clean/readable output in the face of:
   - parallel command execution
   - multiple target systems
   - variable order of commands (dependencies may change the obvious order when flattened to a list) 
 - environment where end-users can add (a la sbt's projects
   - custom atomic mandates (system-specific SSH commands) with prerequisites and consequences
   - custom composite mandates (aggregations of other mandates) to make higher-level directives
   - custom resources that were not foreseen in the jibe core

Things to decide on:
 - What's the best way to represent the source structure in the output once the list of commands is flattened?
 - Should the scala code be able to get data from the stdout of commands or should all decisions be made inside the mandates?
 - What else should be resources?  Is the weak-matching-by-name-only thing I've done so far sufficient?
   - How can you handle things like one mandate producing a directory of files while another uses only one file in that directory?
   - How do you handle negative resources?  A "userdel" command's consequence is the removal of a resource.  Can a mandate depend on that?