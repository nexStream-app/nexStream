<?php
// api/sports.php — Sport listings API. Requires Bearer licence token.
require_once dirname(__DIR__) . '/includes/db.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(200); exit; }
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['success' => false, 'error' => 'Method not allowed']);
    exit;
}

$db = db();

$date     = isset($_GET['date'])     ? $_GET['date']     : date('Y-m-d');
$force    = isset($_GET['force'])    && $_GET['force']   === '1';
$category = isset($_GET['category']) ? trim($_GET['category']) : null; // e.g. ?category=rugby
$replay   = isset($_GET['replay'])   && $_GET['replay']  === '1';

if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) $date = date('Y-m-d');

// Scrape if forced or no events exist yet for today
if ($date === date('Y-m-d')) {
    try {
        $check = $db->prepare("SELECT COUNT(*) FROM sports_listings WHERE event_date = ?");
        $check->execute([$date]);
        $hasEvents = (int)$check->fetchColumn() > 0;
    } catch (PDOException $e) { $hasEvents = false; }

    if ($force || !$hasEvents) {
        require_once dirname(__DIR__) . '/cron/scrape_sports.php';
        runSportsScrape(false);
    }
}

$where  = ['event_date = ?'];
$params = [$date];

if ($category !== null && $category !== '') {
    $where[]  = 'source_category = ?';
    $params[] = $category;
}
if (!$replay) {
    $where[] = '(is_replay = 0 OR is_replay IS NULL)';
}

$whereSQL = 'WHERE ' . implode(' AND ', $where);

try {
    $stmt = $db->prepare("
        SELECT id, sport_category, sport_logo_url, event_name,
               time_uk, time_et, channels,
               source_category, group_name, start_utc, end_utc, is_replay, channel_group_hint
        FROM sports_listings
        $whereSQL
        ORDER BY COALESCE(start_utc, CONCAT(event_date, ' 12:00:00')) ASC, id ASC
    ");
    $stmt->execute($params);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    // Table may not yet exist (first run before any scrape)
    $rows = [];
}

$events = [];
foreach ($rows as $row) {
    $channels = json_decode($row['channels'] ?? '[]', true);
    if (!is_array($channels)) $channels = [];

    $events[] = [
        'id'              => (int) $row['id'],
        'sport_category'  => $row['sport_category']   ?? '',
        'sport_logo_url'  => $row['sport_logo_url']   ?? '',
        'event_name'      => $row['event_name']        ?? '',
        'time_uk'         => $row['time_uk']            ?? '',
        'time_et'         => $row['time_et']            ?? '',
        'channels'        => $channels,
        'source_category'    => $row['source_category']      ?? '',
        'group_name'         => $row['group_name']            ?? '',
        'start_utc'          => $row['start_utc']             ?? null,
        'end_utc'            => $row['end_utc']               ?? null,
        'is_replay'          => (int)($row['is_replay']       ?? 0),
        'channel_group_hint' => $row['channel_group_hint']    ?? null,
    ];
}

// Distinct source categories available (for filtering in app)
try {
    $catStmt = $db->prepare("SELECT DISTINCT source_category FROM sports_listings WHERE event_date = ? AND source_category IS NOT NULL ORDER BY source_category");
    $catStmt->execute([$date]);
    $availableCategories = $catStmt->fetchAll(PDO::FETCH_COLUMN);
} catch (PDOException $e) { $availableCategories = []; }

echo json_encode([
    'success'              => true,
    'date'                 => $date,
    'events'               => $events,
    'available_categories' => $availableCategories,
    'attribution'          => 'Source: Daily Sports Guide (dailysportsguide.co.uk)',
]);
