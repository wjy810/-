Place the operator's official `terms.md` and `privacy.md` here (UTF-8 Markdown: `#` title, `##`
section headings, paragraphs and `-` list items). They are mounted read-only into the app at
`/etc/jobproof/policies/`. Until both exist, the site shows the built-in reference text marked as
unpublished and production keeps registration closed. Bump `JOBPROOF_TERMS_VERSION` /
`JOBPROOF_PRIVACY_VERSION` whenever a text changes so the version recorded at registration matches; restart the app
to load new files.
