-- Before readable file names were kept, a Chinese upload name was stored as underscores
-- ("____.pdf"). The original cannot be recovered; name such files plainly instead.
UPDATE career_library_files
SET display_name = CONCAT('未命名文件', SUBSTRING(display_name, LOCATE('.', display_name)))
WHERE REGEXP_LIKE(display_name, '^_+[.][A-Za-z0-9]{1,8}$');
