We are not monkey to do it by hand. 

Convert java to kotlin as usual and just run script in the idea project dir. VCS mappings are used to support several repositories.

Converted files will be committed and history is preserved (only and only converted files will be committed — working directory can be dirty).

What script does:

1. kotlin file renamed to java file (foo.kt -> foo.java).
2. first commit.
3. foo.java renamed back to foo.kt.
4. second (and last) commit.

Please note — only and only converted files will be committed, so, you should commit another changed files (e.g. if some java file modified to use kotlin API).

Works for me on OS X. Create path to backup your changes before run. Be ready to revert commits (git reset HEAD~1). 
