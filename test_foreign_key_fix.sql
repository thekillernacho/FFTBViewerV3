-- Test the foreign key constraint fix
-- This demonstrates that we can now safely delete songs with track plays

-- Step 1: Create a test scenario
SELECT 'Creating test song...' as step;
INSERT INTO songs (title, duration, created_at, occurrence) 
VALUES ('Test Song for Foreign Key Fix', '3:00', NOW(), 1);

-- Get the song ID
SELECT id, title FROM songs WHERE title = 'Test Song for Foreign Key Fix';

-- Step 2: Create a track play for this song 
SELECT 'Creating track play for test song...' as step;
INSERT INTO track_plays (song_id, played_at) 
VALUES ((SELECT id FROM songs WHERE title = 'Test Song for Foreign Key Fix'), NOW());

-- Verify the track play was created
SELECT tp.id, s.title, tp.played_at 
FROM track_plays tp 
JOIN songs s ON tp.song_id = s.id 
WHERE s.title = 'Test Song for Foreign Key Fix';

-- Step 3: Test our fix - delete track plays first, then song
SELECT 'Testing foreign key constraint fix...' as step;

-- This is what our new method does: delete track plays first
DELETE FROM track_plays 
WHERE song_id IN (SELECT id FROM songs WHERE title = 'Test Song for Foreign Key Fix');

-- Now we can safely delete the song
DELETE FROM songs WHERE title = 'Test Song for Foreign Key Fix';

-- Verify cleanup was successful
SELECT 'Verification - should return 0 rows:' as step;
SELECT COUNT(*) as remaining_test_records 
FROM songs s 
LEFT JOIN track_plays tp ON tp.song_id = s.id 
WHERE s.title = 'Test Song for Foreign Key Fix';
