<?php
/**
 * NexStream Sports Guide Scraper — dailysportsguide.co.uk
 *
 * CLI: php scrape_sports.php
 *      php scrape_sports.php --category=rugby
 *      php scrape_sports.php --dry-run
 *
 * IONOS cron: *\/20 * * * * php /home/www/public/nexstream/cron/scrape_sports.php
 * Attribution: Source: Daily Sports Guide (dailysportsguide.co.uk)
 */

require_once dirname(__DIR__) . '/includes/db.php';

// ── CONFIGURATION ─────────────────────────────────────────────────────────────

define('DSG_BASE_URL',         'https://dailysportsguide.co.uk/');
define('DSG_USER_AGENT',       'nexStream/1.0 (+https://nexstream.uk)');
define('DSG_FRESHNESS_HOURS',  48);
define('DSG_REQUEST_DELAY_US', 1000000); // 1 s between requests

// Source-category keys (internal DB key → also used as default URL slug).
// These are SEED DEFAULTS — live config is read from sports_page_config in the DB.
const DSG_CATEGORIES = [
    'live', 'ppv', 'premier-league', 'EFL', 'Scottish-Football',
    'irishsport', 'UEFA-Competitions', 'fa-cup', 'Europeanfootball', 'international',
    'dazn', 'ufc-fite', 'matchroom', 'espn', 'motorsport', 'rugby',
    'usasport', 'canadasport', 'au-nzsport', 'amazon', 'womensfootball',
];

const DSG_CATEGORY_LABELS = [
    'live'             => 'Live Sports',
    'ppv'              => 'PPV',
    'premier-league'   => 'Premier League',
    'EFL'              => 'EFL',
    'Scottish-Football'=> 'Scottish Football',
    'irishsport'       => 'Irish Sport',
    'UEFA-Competitions'=> 'UEFA Competitions',
    'fa-cup'           => 'FA Cup',
    'Europeanfootball' => 'European Football',
    'international'    => 'International',
    'dazn'             => 'DAZN',
    'ufc-fite'         => 'UFC / Fite',
    'matchroom'        => 'Matchroom',
    'espn'             => 'ESPN+',
    'motorsport'       => 'Motorsport',
    'rugby'            => 'Rugby',
    'usasport'         => 'USA Sport',
    'canadasport'      => 'Canada Sport',
    'au-nzsport'       => 'AU / NZ Sport',
    'amazon'           => 'Amazon',
    'womensfootball'   => "Women's Football",
];

// ── SCHEMA SETUP ─────────────────────────────────────────────────────────────

function dsgEnsureSchema(PDO $db): void {
    $db->exec("CREATE TABLE IF NOT EXISTS sports_listings (
        id             INT AUTO_INCREMENT PRIMARY KEY,
        sport_category VARCHAR(100),
        sport_logo_url VARCHAR(500) DEFAULT '',
        event_name     VARCHAR(255),
        time_uk        VARCHAR(30),
        time_et        VARCHAR(30),
        channels       TEXT,
        event_date     DATE,
        created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_sports_date (event_date)
    )");

    $newCols = [
        'source_category'    => "VARCHAR(50) NULL",
        'group_name'         => "VARCHAR(255) NULL",
        'start_utc'          => "DATETIME NULL",
        'end_utc'            => "DATETIME NULL",
        'content_hash'       => "CHAR(64) NULL",
        'format_matched'     => "VARCHAR(5) NULL",
        'is_replay'          => "TINYINT(1) DEFAULT 0",
        'scraped_run_id'     => "BIGINT NULL",
        'channel_group_hint' => "VARCHAR(100) NULL",
        'last_scraped_date'  => "DATE NULL",
    ];
    foreach ($newCols as $col => $def) {
        try { $db->exec("ALTER TABLE sports_listings ADD COLUMN $col $def"); }
        catch (PDOException $e) {}
    }
    try { $db->exec("ALTER TABLE sports_listings ADD UNIQUE KEY uk_content_hash (content_hash)"); }
    catch (PDOException $e) {}
    try { $db->exec("ALTER TABLE sports_listings ADD INDEX idx_start_utc (start_utc)"); }
    catch (PDOException $e) {}
    try { $db->exec("ALTER TABLE sports_listings ADD INDEX idx_source_cat (source_category)"); }
    catch (PDOException $e) {}

    $db->exec("CREATE TABLE IF NOT EXISTS sports_scrape_log (
        id               INT AUTO_INCREMENT PRIMARY KEY,
        ran_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        events_inserted  INT DEFAULT 0,
        success          TINYINT(1) DEFAULT 1,
        error_msg        TEXT
    )");
    $logCols = [
        'categories_scraped' => 'INT DEFAULT 0',
        'categories_failed'  => 'INT DEFAULT 0',
        'stale_discarded'    => 'INT DEFAULT 0',
        'unmatched_logged'   => 'INT DEFAULT 0',
    ];
    foreach ($logCols as $col => $def) {
        try { $db->exec("ALTER TABLE sports_scrape_log ADD COLUMN $col $def"); }
        catch (PDOException $e) {}
    }

    $db->exec("CREATE TABLE IF NOT EXISTS sports_unmatched_blocks (
        id        INT AUTO_INCREMENT PRIMARY KEY,
        category  VARCHAR(50),
        raw_text  TEXT,
        logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX (logged_at)
    )");

    // Page config table — stores editable URL slugs and active flags per category
    $db->exec("CREATE TABLE IF NOT EXISTS sports_page_config (
        id              INT AUTO_INCREMENT PRIMARY KEY,
        source_category VARCHAR(50) NOT NULL,
        label           VARCHAR(100) NOT NULL,
        url_slug        VARCHAR(200) NOT NULL,
        is_active       TINYINT(1) NOT NULL DEFAULT 1,
        sort_order      INT NOT NULL DEFAULT 0,
        updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_spc_cat (source_category)
    )");

    // Add source_tz column if not already present
    try { $db->exec("ALTER TABLE sports_page_config ADD COLUMN source_tz VARCHAR(50) NOT NULL DEFAULT 'Europe/London'"); }
    catch (PDOException $e) {}
    // Add parser_format column if not already present (NULL = auto/category-based dispatch)
    try { $db->exec("ALTER TABLE sports_page_config ADD COLUMN parser_format VARCHAR(20) NULL"); }
    catch (PDOException $e) {}

    // Seed defaults — INSERT IGNORE preserves any admin edits
    // Columns: source_category, label, url_slug, is_active, sort_order, source_tz
    $seed = [
        ['live',              'Live Sports',       'live',              1,  0, 'Europe/London'],
        ['ppv',               'PPV',               'ppv',               1,  1, 'Europe/London'],
        ['premier-league',    'Premier League',    'premier-league',    1,  2, 'Europe/London'],
        ['EFL',               'EFL',               'EFL',               1,  3, 'Europe/London'],
        ['Scottish-Football', 'Scottish Football', 'Scottish-Football', 1,  4, 'Europe/London'],
        ['irishsport',        'Irish Sport',       'irishsport',        1,  5, 'Europe/London'],
        ['UEFA-Competitions', 'UEFA Competitions', 'UEFA-Competitions', 1,  6, 'Europe/London'],
        ['fa-cup',            'FA Cup',            'fa-cup',            1,  7, 'Europe/London'],
        ['Europeanfootball',  'European Football', 'Europeanfootball',  1,  8, 'Europe/London'],
        ['international',     'International',     'international',     1,  9, 'Europe/London'],
        ['dazn',              'DAZN',              'dazn',              1, 10, 'Europe/London'],
        ['ufc-fite',          'UFC / Fite',        'ufc-fite',          1, 11, 'Europe/London'],
        ['matchroom',         'Matchroom',         'matchroom',         1, 12, 'Europe/London'],
        ['espn',              'ESPN+',             'espn',              1, 13, 'America/New_York'],
        ['motorsport',        'Motorsport',        'motorsport',        1, 14, 'Europe/London'],
        ['rugby',             'Rugby',             'rugby',             1, 15, 'Europe/London'],
        ['usasport',          'USA Sport',         'usasport',          1, 16, 'America/New_York'],
        ['canadasport',       'Canada Sport',      'canadasport',       1, 17, 'America/Toronto'],
        ['au-nzsport',        'AU / NZ Sport',     'au-nzsport',        1, 18, 'Pacific/Auckland'],
        ['amazon',            'Amazon',            'amazon',            1, 19, 'Europe/London'],
        ['womensfootball',    "Women's Football",  'womensfootball',    1, 20, 'Europe/London'],
    ];
    $ins = $db->prepare("INSERT IGNORE INTO sports_page_config (source_category, label, url_slug, is_active, sort_order, source_tz) VALUES (?,?,?,?,?,?)");
    foreach ($seed as $row) { $ins->execute($row); }
}

// ── PAGE CONFIG ───────────────────────────────────────────────────────────────

/**
 * Returns active page configs from DB: [['category', 'slug', 'label'], ...]
 */
function dsgLoadPageConfig(PDO $db): array {
    try {
        $rows = $db->query("SELECT source_category, label, url_slug, COALESCE(source_tz,'Europe/London') AS source_tz, parser_format FROM sports_page_config WHERE is_active = 1 ORDER BY sort_order, source_category")
                   ->fetchAll(PDO::FETCH_ASSOC);
        return array_map(fn($r) => [
            'category'      => $r['source_category'],
            'slug'          => $r['url_slug'],
            'label'         => $r['label'],
            'tz'            => $r['source_tz'],
            'parser_format' => $r['parser_format'] ?? null,
        ], $rows);
    } catch (PDOException $e) {
        // Fallback to constants if table not yet available
        return array_map(fn($cat) => [
            'category' => $cat,
            'slug'     => $cat,
            'label'    => DSG_CATEGORY_LABELS[$cat] ?? $cat,
            'tz'       => 'Europe/London',
        ], DSG_CATEGORIES);
    }
}

// ── HTTP FETCH ─────────────────────────────────────────────────────────────

function dsgFetchHtml(string $url): string {
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_MAXREDIRS      => 5,
        CURLOPT_TIMEOUT        => 30,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_USERAGENT      => DSG_USER_AGENT,
        CURLOPT_HTTPHEADER     => ['Accept: text/html', 'Accept-Language: en-GB,en;q=0.9'],
        CURLOPT_ENCODING       => '',
        CURLOPT_SSL_VERIFYPEER => true,
    ]);
    $html = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    if ($html === false || $err) throw new RuntimeException("cURL: $err");
    if ($code < 200 || $code >= 300) throw new RuntimeException("HTTP $code from $url");
    return $html;
}

// ── NODE EXTRACTION ──────────────────────────────────────────────────────────

/**
 * Returns a flat array of ['tag'=>string, 'text'=>string, 'bold'=>bool]
 * extracted from the Elementor text-editor content div.
 */
function dsgExtractItems(string $html): array {
    libxml_use_internal_errors(true);
    $dom = new DOMDocument();
    @$dom->loadHTML('<?xml encoding="UTF-8">' . $html);
    libxml_clear_errors();
    $xpath = new DOMXPath($dom);

    // Primary: Elementor widget text editor → widget-container child
    $containers = $xpath->query(
        '//div[contains(@class,"elementor-widget-text-editor")]//div[contains(@class,"elementor-widget-container")]'
    );
    // Fallback: Elementor text-editor class (older Elementor versions)
    if (!$containers || $containers->length === 0) {
        $containers = $xpath->query(
            '//div[contains(@class,"elementor-widget-text-editor")]//div[contains(@class,"elementor-text-editor")]'
        );
    }
    // Fallback: WordPress entry content
    if (!$containers || $containers->length === 0) {
        $containers = $xpath->query('//div[contains(@class,"entry-content")]');
    }
    if (!$containers || $containers->length === 0) return [];

    $items = [];
    for ($ci = 0; $ci < $containers->length; $ci++) {
        foreach ($containers->item($ci)->childNodes as $node) {
            dsgWalkNode($node, $items, $xpath);
        }
    }

    return $items;
}

function dsgWalkNode(DOMNode $node, array &$items, DOMXPath $xpath): void {
    $tag = strtolower($node->nodeName);
    if ($tag === '#text' || $tag === '#comment') return;

    if ($tag === 'hr') {
        $items[] = ['tag' => 'hr', 'text' => '', 'bold' => false];
        return;
    }

    if ($tag === 'ul') {
        foreach ($node->childNodes as $li) {
            if (strtolower($li->nodeName) === 'li') {
                $t = dsgNodeText($li);
                if ($t !== '') $items[] = ['tag' => 'li', 'text' => $t, 'bold' => false];
            }
        }
        return;
    }

    // <p> elements with <br> children: each line is a separate item
    if ($tag === 'p') {
        $hasBr = false;
        foreach ($node->childNodes as $child) {
            if (strtolower($child->nodeName) === 'br') { $hasBr = true; break; }
        }
        if ($hasBr) {
            $segment = '';
            $segBold = false;
            foreach ($node->childNodes as $child) {
                $cTag = strtolower($child->nodeName);
                if ($cTag === 'br') {
                    $t = trim(preg_replace('/\s+/', ' ', $segment));
                    if ($t !== '') $items[] = ['tag' => 'p', 'text' => $t, 'bold' => $segBold];
                    $segment = '';
                    $segBold = false;
                } elseif ($cTag === 'strong' || $cTag === 'b') {
                    $segment .= $child->textContent;
                    $segBold = true;
                } else {
                    $segment .= $child->textContent;
                }
            }
            $t = trim(preg_replace('/\s+/', ' ', $segment));
            if ($t !== '') $items[] = ['tag' => 'p', 'text' => $t, 'bold' => $segBold];
            return;
        }
    }

    if (in_array($tag, ['h1','h2','h3','h4','p','div','span'])) {
        $text = dsgNodeText($node);
        if ($text === '') return;

        // Detect bold: entire text wrapped in <strong>
        $isBold = false;
        $strongs = $xpath->query('.//strong', $node);
        if ($strongs && $strongs->length > 0) {
            $boldText = '';
            foreach ($strongs as $s) $boldText .= $s->textContent;
            $isBold = (trim($boldText) === $text);
        }

        $items[] = ['tag' => $tag, 'text' => $text, 'bold' => $isBold];
        return;
    }

    // Recurse into unknown containers
    foreach ($node->childNodes as $child) {
        dsgWalkNode($child, $items, $xpath);
    }
}

function dsgNodeText(DOMNode $node): string {
    return trim(preg_replace('/\s+/', ' ', $node->textContent));
}

// ── DATETIME HELPERS ─────────────────────────────────────────────────────────

/** Months table used by multiple parsers. */
function dsgMonthNum(string $m): int {
    static $map = [
        'jan'=>1,'january'=>1, 'feb'=>2,'february'=>2, 'mar'=>3,'march'=>3,
        'apr'=>4,'april'=>4,   'may'=>5,               'jun'=>6,'june'=>6,
        'jul'=>7,'july'=>7,    'aug'=>8,'august'=>8,   'sep'=>9,'september'=>9,
        'oct'=>10,'october'=>10,'nov'=>11,'november'=>11,'dec'=>12,'december'=>12,
    ];
    return $map[strtolower(trim($m))] ?? 0;
}

/**
 * Parse a time string like "3:00pm", "15:30", "3:00 PM", "12:30am"
 * Returns [hour, minute] in 24h, or null.
 */
function dsgParseTime(string $s): ?array {
    $s = preg_replace('/\s*(UK|BST|GMT|ET|EST|EDT|UTC)\b.*/i', '', trim($s));
    $s = preg_replace('/\s*\/.*$/', '', $s); // strip " / ET time" part
    $s = trim($s);
    if (preg_match('/^(\d{1,2}):(\d{2})\s*(am|pm)?$/i', $s, $m)) {
        $h = (int)$m[1]; $min = (int)$m[2];
        if (!empty($m[3])) {
            if (strtolower($m[3]) === 'pm' && $h !== 12) $h += 12;
            if (strtolower($m[3]) === 'am' && $h === 12) $h = 0;
        }
        return [$h, $min];
    }
    return null;
}

/**
 * Parse a date string. Returns 'YYYY-MM-DD' or null.
 * Handles: "Saturday, 25th July", "25th July", "DD-MM-YYYY", "DD/MM/YYYY", "DD.MM.YYYY"
 */
function dsgParseDate(string $s): ?string {
    $s = trim($s);

    // DD-MM-YYYY or DD/MM/YYYY
    if (preg_match('/^(\d{1,2})[-\/](\d{2})[-\/](\d{4})$/', $s, $m))
        return sprintf('%04d-%02d-%02d', $m[3], $m[2], $m[1]);

    // DD.MM.YYYY
    if (preg_match('/^(\d{1,2})\.(\d{2})\.(\d{4})$/', $s, $m))
        return sprintf('%04d-%02d-%02d', $m[3], $m[2], $m[1]);

    // YYYY-MM-DD
    if (preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $s, $m))
        return $s;

    // "Saturday, 25th July" or "25th July" or "30th June '26"
    if (preg_match('/(\d{1,2})(?:st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December)(?:\s+\'?(\d{2,4}))?/i', $s, $m)) {
        $day  = (int)$m[1];
        $mon  = dsgMonthNum($m[2]);
        $year = isset($m[3]) && $m[3] !== '' ? (int)$m[3] : (int)date('Y');
        if ($year < 100) $year += 2000;
        if ($mon < 1) return null;
        return sprintf('%04d-%02d-%02d', $year, $mon, $day);
    }

    // "25th Nov" short form (no year - assume current or next year)
    if (preg_match('/(\d{1,2})(?:st|nd|rd|th)?\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)/i', $s, $m)) {
        $day = (int)$m[1];
        $mon = dsgMonthNum($m[2]);
        if ($mon < 1) return null;
        $year = (int)date('Y');
        // If month already passed this year, it's probably next year
        if ($mon < (int)date('n')) $year++;
        return sprintf('%04d-%02d-%02d', $year, $mon, $day);
    }

    return null;
}

/**
 * Normalize to UTC datetime string 'YYYY-MM-DD HH:MM:SS'.
 * $dateStr: any format accepted by dsgParseDate()
 * $timeStr: any format accepted by dsgParseTime(), or null
 * $tz: source timezone name
 */
function dsgToUtc(string $dateStr, ?string $timeStr = null, string $tz = 'Europe/London'): ?string {
    $date = dsgParseDate($dateStr);
    if (!$date) return null;

    $time = [0, 0];
    if ($timeStr !== null) {
        $parsed = dsgParseTime($timeStr);
        if ($parsed) $time = $parsed;
    }

    try {
        $dt = new DateTime("$date {$time[0]}:{$time[1]}:00", new DateTimeZone($tz));
        $dt->setTimezone(new DateTimeZone('UTC'));
        return $dt->format('Y-m-d H:i:s');
    } catch (Throwable $e) {
        return null;
    }
}

/**
 * Parse "DD-MM-YYYY HH:MM [AM|PM]" or "DD/MM/YYYY HH:MM [AM|PM]" inline strings.
 * Returns UTC string or null.
 */
function dsgParseDatetimeInline(string $s, string $tz = 'Europe/London'): ?string {
    $s = trim($s);
    // "DD-MM-YYYY HH:MM" or "DD-MM-YYYY HH:MM AM"
    if (preg_match('/^(\d{1,2})[-\/](\d{2})[-\/](\d{4})\s+(\d{1,2}):(\d{2})(?:\s*(AM|PM))?$/i', $s, $m)) {
        $h = (int)$m[4];
        if (!empty($m[6])) {
            if (strtolower($m[6]) === 'pm' && $h !== 12) $h += 12;
            if (strtolower($m[6]) === 'am' && $h === 12) $h = 0;
        }
        $date = sprintf('%04d-%02d-%02d', $m[3], $m[2], $m[1]);
        try {
            $dt = new DateTime("$date $h:{$m[5]}:00", new DateTimeZone($tz));
            $dt->setTimezone(new DateTimeZone('UTC'));
            return $dt->format('Y-m-d H:i:s');
        } catch (Throwable $e) { return null; }
    }
    // "YYYY-MM-DD HH:MM:SS" ISO
    if (preg_match('/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}(:\d{2})?)$/', $s, $m)) {
        try {
            $dt = new DateTime($s, new DateTimeZone($tz));
            $dt->setTimezone(new DateTimeZone('UTC'));
            return $dt->format('Y-m-d H:i:s');
        } catch (Throwable $e) { return null; }
    }
    return null;
}

/**
 * Parse "UK Wed 12 Nov 5:57pm" → UTC string.
 */
function dsgParseRegionalDatetime(string $s, string $tz = 'Europe/London'): ?string {
    // "Wed 12 Nov 5:57pm" or "UK Wed 12 Nov 5:57pm"
    if (preg_match('/(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}):(\d{2})(am|pm)/i', $s, $m)) {
        $day = (int)$m[1];
        $mon = dsgMonthNum($m[2]);
        $h   = (int)$m[3]; $min = (int)$m[4];
        if (strtolower($m[5]) === 'pm' && $h !== 12) $h += 12;
        if (strtolower($m[5]) === 'am' && $h === 12) $h = 0;
        $year = (int)date('Y');
        if ($mon < (int)date('n')) $year++;
        $date = sprintf('%04d-%02d-%02d', $year, $mon, $day);
        try {
            $dt = new DateTime("$date $h:$min:00", new DateTimeZone($tz));
            $dt->setTimezone(new DateTimeZone('UTC'));
            return $dt->format('Y-m-d H:i:s');
        } catch (Throwable $e) { return null; }
    }
    return null;
}

/** UK time string from UTC (for the time_uk column). */
function dsgUtcToUkTime(?string $utc): string {
    if (!$utc) return '';
    try {
        $dt = new DateTime($utc, new DateTimeZone('UTC'));
        $dt->setTimezone(new DateTimeZone('Europe/London'));
        return $dt->format('H:i');
    } catch (Throwable $e) { return ''; }
}

// ── FRESHNESS FILTER ─────────────────────────────────────────────────────────

function dsgIsFresh(?string $startUtc): bool {
    if ($startUtc === null) return true; // no date info → assume it's current
    try {
        $cutoff = new DateTime('now', new DateTimeZone('UTC'));
        $cutoff->modify('-' . DSG_FRESHNESS_HOURS . ' hours');
        $start = new DateTime($startUtc, new DateTimeZone('UTC'));
        return $start >= $cutoff;
    } catch (Throwable $e) { return true; }
}

// ── CONTENT HASH ─────────────────────────────────────────────────────────────

function dsgHash(string $category, string $group, string $event, ?string $startUtc): string {
    return hash('sha256', "$category|$group|$event|$startUtc");
}

// ── FORMAT PARSERS ────────────────────────────────────────────────────────────
// Each returns an array of normalised event arrays.

/**
 * Build an event array from components. All fields are strings/arrays.
 */
function dsgEvent(
    string $category, string $group, string $eventName,
    ?string $startUtc, ?string $endUtc, array $channels,
    string $format, bool $isReplay = false, ?string $channelGroupHint = null
): array {
    $date   = $startUtc ? substr($startUtc, 0, 10) : date('Y-m-d');
    $timeUk = dsgUtcToUkTime($startUtc);

    return [
        'sport_category'     => DSG_CATEGORY_LABELS[$category] ?? $category,
        'sport_logo_url'     => '',
        'event_name'         => mb_substr(trim($eventName), 0, 255),
        'time_uk'            => $timeUk,
        'time_et'            => '',
        'channels'           => array_values(array_unique(array_filter(array_map('trim', $channels)))),
        'event_date'         => $date,
        'source_category'    => $category,
        'group_name'         => mb_substr(trim($group), 0, 255),
        'start_utc'          => $startUtc,
        'end_utc'            => $endUtc,
        'format_matched'     => $format,
        'is_replay'          => $isReplay ? 1 : 0,
        'content_hash'       => dsgHash($category, $group, $eventName, $startUtc),
        'channel_group_hint' => $channelGroupHint,
    ];
}

/**
 * Main sequential parser — handles Formats A, B, C, D, E, G, H, J.
 * Works as a state machine over the node item list.
 */
function dsgParseItems(array $items, string $category, string $tz = 'Europe/London', ?string $forceFormat = null): array {
    $events    = [];
    $unmatched = [];
    $label     = DSG_CATEGORY_LABELS[$category] ?? $category;

    // ── Empty-state check ──
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events' => [], 'unmatched' => []];

    // Resolve format: explicit override wins, otherwise use category defaults
    $fmt = ($forceFormat !== null && $forceFormat !== '' && $forceFormat !== 'auto')
        ? $forceFormat
        : match($category) {
            'au-nzsport'  => 'M',
            'irishsport'  => 'L',
            'canadasport' => 'I',
            'amazon'      => 'H',
            default       => 'general',
        };

    if ($fmt === 'M') return dsgParseFormatM($items, $category, $tz);
    if ($fmt === 'L') return dsgParseFormatL($items, $category);
    if ($fmt === 'I') return dsgParseFormatI($items, $category);
    if ($fmt === 'H') return dsgParseFormatH($items, $category);

    // ── General state-machine for A/B/C/D/E/G ──
    $group       = $label;
    $currentDate = null;
    $currentTime = null;
    $pendingName = null;
    $pendingChs  = [];

    $flush = function() use (&$events, &$pendingName, &$pendingChs, &$currentTime,
                              &$currentDate, &$group, $category) {
        if ($pendingName === null) return;
        // Bold text with no channels and no "vs/v" match pattern → section group header
        if (empty($pendingChs)
            && !preg_match('/\s+vs\.?\s+|\s+v\s+/i', $pendingName)) {
            $group = $pendingName;
            $pendingName = null;
            $pendingChs  = [];
            return;
        }
        $startUtc = null;
        if ($currentDate) {
            $startUtc = dsgToUtc($currentDate, $currentTime);
        } elseif ($currentTime) {
            $startUtc = dsgToUtc(date('Y-m-d'), $currentTime);
        }
        $events[] = dsgEvent($category, $group, $pendingName, $startUtc, null, $pendingChs, 'A');
        $pendingName = null;
        $pendingChs  = [];
    };

    $n = count($items);
    for ($i = 0; $i < $n; $i++) {
        $item = $items[$i];
        $tag  = $item['tag'];
        $text = $item['text'];
        $bold = $item['bold'];

        // h3 = new group/broadcaster section
        if ($tag === 'h3') {
            $flush();
            $group       = $text;
            $currentDate = null;
            $currentTime = null;
            continue;
        }

        // <hr> or bare en-dash = separator between events within a group
        if ($tag === 'hr' || ($tag === 'p' && in_array($text, ['–', '—', '-', '—-', '– –']))) {
            $flush();
            $currentTime = null; // reset so next ALL CAPS line is a group header, not an event
            continue;
        }

        // ===== = Format B block separator
        if (preg_match('/^={3,}$/', $text)) {
            $flush();
            continue;
        }

        // ── Format E: "– DD-MM-YYYY HH:MM [AM|PM] until DD/MM/YYYY HH:MM [AM|PM] – Channel" ──
        if (preg_match(
            '/^[–\-]\s*(\d{1,2}[\-\/]\d{2}[\-\/]\d{4})\s+(\d{1,2}:\d{2}(?:\s*[AP]M)?)\s+until\s+(\d{1,2}[\-\/]\d{2}[\-\/]\d{4})\s+(\d{1,2}:\d{2}(?:\s*[AP]M)?)\s*[–\-]\s*(.+)$/i',
            $text, $m
        )) {
            $startUtc = dsgParseDatetimeInline($m[1] . ' ' . $m[2], $tz);
            $endUtc   = dsgParseDatetimeInline($m[3] . ' ' . $m[4], $tz);
            $channel  = trim($m[5]);
            $eName    = $pendingName ?? '';
            $pendingName = null; $pendingChs = [];
            if ($eName !== '') {
                $events[] = dsgEvent($category, $group, $eName, $startUtc, $endUtc, [$channel], 'E');
            }
            continue;
        }

        // ── Format G: ISO datetime inline "start:YYYY-MM-DD HH:MM:SS stop:..." ──
        if (preg_match('/start:(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})(?:\s+stop:(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}))?/', $text, $m)) {
            $prefix    = trim(preg_replace('/\s+start:.*$/', '', $text));
            $parts     = preg_split('/\s*:\s*/', $prefix, 2);
            $channel   = trim($parts[0] ?? '');
            $eName     = trim($parts[1] ?? $prefix);
            $startUtc  = dsgParseDatetimeInline($m[1], 'UTC'); // ISO times are already UTC on this site
            $endUtc    = isset($m[2]) ? dsgParseDatetimeInline($m[2], 'UTC') : null;
            if ($eName !== '') {
                $events[] = dsgEvent($category, $group, $eName, $startUtc, $endUtc, $channel ? [$channel] : [], 'G');
            }
            continue;
        }

        // ── Format C: numbered time-first "01 | HH:MM Event" or "Prefix 01 | HH:MM Event" ──
        if (preg_match('/^((?:[A-Za-z][A-Za-z\s+]*\s+)?)(\d{2})\s*\|\s*(\d{1,2}:\d{2})\s+(.{2,})$/', $text, $m)
            && strpos($text, 'until') === false) {
            $flush();
            $prefix   = trim($m[1]);   // e.g. "Friendly", "FA Cup", "" (empty for PL/EFL bare slots)
            $num      = $m[2];         // e.g. "01"
            $timeStr  = $m[3];
            $eName    = trim($m[4]);
            // With prefix → full channel name e.g. "FA Cup 01"; without → bare slot "01" + group hint
            $chanName = $prefix !== '' ? $prefix . ' ' . $num : $num;
            $hint     = $prefix === '' ? 'Todays Live Events' : null;
            $startUtc = $currentDate
                ? dsgToUtc($currentDate, $timeStr)
                : dsgToUtc(date('Y-m-d'), $timeStr);
            $events[] = dsgEvent($category, $group, $eName, $startUtc, null, $chanName !== '' ? [$chanName] : [], 'C', false, $hint);
            continue;
        }

        // ── Format D: numbered title-first "Prefix 01 | Title | HH:MM"
        //    or "Prefix 01 | Title | HH:MM" (title before time) ──
        if (preg_match('/^(.+?)\s+(\d{2})\s*\|\s*(.+?)\s*\|\s*(\d{1,2}:\d{2})$/', $text, $m)) {
            $flush();
            $channel  = trim($m[1]) . ' ' . $m[2];
            $eName    = trim($m[3]);
            $timeStr  = $m[4];
            $startUtc = $currentDate
                ? dsgToUtc($currentDate, $timeStr)
                : dsgToUtc(date('Y-m-d'), $timeStr);
            $events[] = dsgEvent($category, $group, $eName, $startUtc, null, [$channel], 'D');
            continue;
        }

        // ── Rugby Format G without ISO: "Channel N: Team1 HH:MM Team2" ──
        if (preg_match('/^(.+?)\s+(\d{1,3}):\s*(.+?)\s+(\d{1,2}:\d{2})\s+(.+)$/', $text, $m)
            && strpos($text, '|') === false && strpos($text, '//') === false
            && !preg_match('/\d{4}/', $text)) {
            $flush();
            $channel  = trim($m[1]) . ' ' . $m[2];
            $team1    = trim($m[3]);
            $timeStr  = $m[4];
            $team2    = trim($m[5]);
            $eName    = "$team1 vs $team2";
            $startUtc = $currentDate
                ? dsgToUtc($currentDate, $timeStr)
                : dsgToUtc(date('Y-m-d'), $timeStr);
            $events[] = dsgEvent($category, $group, $eName, $startUtc, null, [$channel], 'G');
            continue;
        }

        // ── Format K: "Prefix NN: Event HH:MM" or "Prefix NN: Team1 HH:MM Team2" (VIP stream / Super League) ──
        // Handles cases where Rugby Format G misses (no mandatory second team after time).
        if (preg_match('/^([A-Za-z].+?)\s+(\d{2,3}):\s*(.+?)\s+(\d{1,2}:\d{2})(?:\s+(.+))?$/', $text, $m)
            && strpos($text, '|') === false
            && !preg_match('/\d{4}/', $text)) {
            $flush();
            $part1    = trim($m[3]);
            $part2    = isset($m[5]) ? trim($m[5]) : '';
            $eName    = $part2 !== '' ? "$part1 vs $part2" : $part1;
            $chanName = trim($m[1]) . ' ' . $m[2];
            $startUtc = $currentDate
                ? dsgToUtc($currentDate, $m[4])
                : dsgToUtc(date('Y-m-d'), $m[4]);
            $events[] = dsgEvent($category, $group, $eName, $startUtc, null, [$chanName], 'K');
            continue;
        }

        // ── Format A state-machine lines ──

        // Date line: "Saturday, 25th July" etc.
        if (preg_match('/\b\d{1,2}(?:st|nd|rd|th)?\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\b/i', $text)) {
            $flush();
            $d = dsgParseDate($text);
            if ($d) $currentDate = $d;
            continue;
        }

        // Time line: "7:45pm UK / 2:45pm ET" or "3:00pm UK" or "12:00am UK THU / ..."
        if (preg_match('/\b(\d{1,2}:\d{2}(?:\s*[ap]m)?)\s+(?:UK|BST|GMT)\b/i', $text, $m)) {
            $currentTime = $m[1];
            continue;
        }

        // Bold line = group header or match/event name (Format A)
        if ($bold && mb_strlen($text) > 2) {
            // "Broadcaster | Category" pattern with no digits → section group header
            if (preg_match('/^[A-Za-z\s&\'\-]+\s*\|\s*[A-Za-z\s&\'\-]+$/', $text) && !preg_match('/\d/', $text)) {
                $flush();
                $group = $text;
                continue;
            }
            if ($pendingName !== null) $flush();
            $pendingName = $text;
            $pendingChs  = [];
            continue;
        }

        // Format B: ALL CAPS = group header (no time, no v/vs) or event name (time set or contains v/vs)
        if ($text === mb_strtoupper($text) && mb_strlen($text) > 3
            && !preg_match('/^\d/', $text)
            && preg_match('/[A-Z]/', $text)
            && !in_array($text, ['UK', 'ET', 'BST', 'USA', 'UFC'])) {
            if ($pendingName === null) {
                if ($currentTime !== null || preg_match('/\s+vs\.?\s+|\s+V\s+/u', $text)) {
                    // Time set or contains match "v/vs" → ALL CAPS event name
                    $pendingName = $text;
                    $pendingChs  = [];
                } else {
                    // No time, no match pattern → section group header
                    $group = $text;
                }
                continue;
            }
        }

        // "TEAM1 v/vs TEAM2" match name (rugby/cricket lowercase-v format) — non-bold fallback
        if ($pendingName === null
            && preg_match('/^[A-Z]{2,}(?:\s+[A-Z]{2,})*\s+v(?:s\.?)?\s+[A-Z]{2,}(?:\s+[A-Z]{2,})*$/', $text)) {
            $pendingName = $text;
            $pendingChs  = [];
            continue;
        }

        // If we have a pending event, accumulate channels or date sub-lines
        if ($pendingName !== null) {
            // Skip page-level noise
            if (strpos($text, 'Source:') === 0 || mb_strlen($text) > 150) continue;
            $pendingChs[] = $text;
            continue;
        }

        // Unmatched — record for review if substantive
        if (mb_strlen($text) > 5 && !preg_match('/^[\-–=]+$/', $text)) {
            $unmatched[] = $text;
        }
    }

    $flush();
    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── FORMAT M (au-nzsport) ─────────────────────────────────────────────────────
// Anchors on the word "until" — no dependency on specific dash character encoding.
// Handles two layouts:
//   Combined: "Event Name – DD-MM-YYYY HH:MM AM until DD/MM/YYYY HH:MM AM – Channel"
//   Separate: event name on one line, "– DD-MM-YYYY ... until ... – Channel" on next line(s)

function dsgParseFormatM(array $items, string $category, string $tz = 'Pacific/Auckland'): array {
    $events = []; $unmatched = [];
    $group  = DSG_CATEGORY_LABELS[$category] ?? $category;
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events'=>[], 'unmatched'=>[]];

    // Date-time-until-date-time block pattern (pure ASCII, no dash dependency)
    $dtPattern = '/(\d{1,2}[\-\/]\d{2}[\-\/]\d{4})\s+(\d{1,2}:\d{2}(?:\s*[AP]M)?)\s+until\s+(\d{1,2}[\-\/]\d{2}[\-\/]\d{4})\s+(\d{1,2}:\d{2}(?:\s*[AP]M)?)/i';

    $pendingName = null;

    foreach ($items as $item) {
        $text = trim($item['text']);
        $tag  = $item['tag'];

        if ($text === '') continue;

        // h3 = section group header
        if ($tag === 'h3') { $group = $text; $pendingName = null; continue; }

        // Bold "Broadcaster | Category" (no digits) = section group header
        if ($item['bold']
            && preg_match('/^[A-Za-z\s&\'\-]+\s*\|\s*[A-Za-z\s&\'\-]+$/', $text)
            && !preg_match('/\d/', $text)) {
            $group = $text; continue;
        }

        // Does this item contain a "date until date" block?
        if (preg_match($dtPattern, $text, $m, PREG_OFFSET_CAPTURE)) {
            $blockStart = $m[0][1];
            $blockLen   = strlen($m[0][0]);

            // Everything before the date block = potential event name (strip leading punctuation/dashes)
            $before  = trim(preg_replace('/^[\s\-\x{2010}-\x{2015}\x{2212}]+/u', '', substr($text, 0, $blockStart)));
            // Everything after the date block = channel (strip leading punctuation, trailing HD/FHD)
            $after   = trim(preg_replace('/^[\s\-\x{2010}-\x{2015}\x{2212}]+/u', '', substr($text, $blockStart + $blockLen)));
            $channel = trim(preg_replace('/\s+[FU]?HD\s*$/i', '', $after));

            // Event name: inline before the block (combined format) or $pendingName (separate format)
            $eName = $before !== '' ? $before : ($pendingName ?? '');

            if ($eName !== '' && $channel !== '') {
                $startUtc = dsgParseDatetimeInline($m[1][0] . ' ' . $m[2][0], $tz);
                $endUtc   = dsgParseDatetimeInline($m[3][0] . ' ' . $m[4][0], $tz);
                $events[] = dsgEvent($category, $group, $eName, $startUtc, $endUtc, [$channel], 'M');
            }

            // Clear pendingName only if we got the name from inline (combined format);
            // keep it for consecutive dash lines in separate format
            if ($before !== '') $pendingName = null;
            continue;
        }

        // No date block — this line is an event name (or group header already handled above)
        $pendingName = $text;
    }

    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── FORMAT L (irishsport) ─────────────────────────────────────────────────────
// Format: "LOI 1 | Event name" on one line, then "start: YYYY-MM-DD HH:MM:SS"
// and optional "stop: YYYY-MM-DD HH:MM:SS" on adjacent lines (br-split or separate p tags).

function dsgParseFormatL(array $items, string $category): array {
    $events = []; $unmatched = [];
    $group  = DSG_CATEGORY_LABELS[$category] ?? $category;
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events'=>[], 'unmatched'=>[]];

    // Pre-merge: append start:/stop: lines onto the preceding event line
    $merged = [];
    foreach ($items as $item) {
        $t = trim($item['text']);
        if (!empty($merged) && preg_match('/^(?:start|stop):\s*\d{4}-\d{2}-\d{2}/i', $t)) {
            $merged[count($merged)-1]['text'] .= ' ' . $t;
        } else {
            $merged[] = $item;
        }
    }

    $currentGroup = $group;
    foreach ($merged as $item) {
        $text = $item['text'];
        if ($item['bold'] && mb_strlen($text) > 2) { $currentGroup = $text; continue; }
        if (preg_match('/^[\-–=]+$/', $text)) continue;

        // "LOI 1 | Waterford v Bohemians start: 2026-08-09 14:45:00 stop: 2026-08-09 17:45:00"
        if (preg_match(
            '/^(.+?)\s*\|\s*(.+?)\s+start:\s*(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})(?:\s+stop:\s*(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}))?/i',
            $text, $m
        )) {
            $channel  = trim($m[1]);
            $eName    = trim($m[2]);
            $startUtc = dsgParseDatetimeInline($m[3], 'UTC');
            $endUtc   = isset($m[4]) ? dsgParseDatetimeInline($m[4], 'UTC') : null;
            if ($eName !== '') {
                $events[] = dsgEvent($category, $currentGroup, $eName, $startUtc, $endUtc, [$channel], 'L');
            }
        } elseif (mb_strlen($text) > 5 && !preg_match('/^[\-–=]+$/', $text)) {
            $unmatched[] = $text;
        }
    }
    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── FORMAT H (amazon) ─────────────────────────────────────────────────────────

function dsgParseFormatH(array $items, string $category): array {
    $events = []; $unmatched = [];
    $group  = DSG_CATEGORY_LABELS[$category] ?? $category;
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events'=>[], 'unmatched'=>[]];

    foreach ($items as $item) {
        $text = $item['text'];
        // "Amazon UK 1 | Eubank Jr. vs. Benn 2: Open Workout // UK Wed 12 Nov 5:57pm // ET ..."
        if (preg_match('/^(.+?)\s*\|\s*(.+?)\s*\/\/\s*UK\s+(.+?)(?:\s*\/\/.*)?$/', $text, $m)) {
            $channel  = trim($m[1]);
            $eName    = trim($m[2]);
            $ukPart   = trim($m[3]);
            $startUtc = dsgParseRegionalDatetime($ukPart, 'Europe/London');
            $events[] = dsgEvent($category, $group, $eName, $startUtc, null, [$channel], 'H');
        } elseif (mb_strlen($text) > 5 && !preg_match('/^[\-–=]+$/', $text)) {
            $unmatched[] = $text;
        }
    }
    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── FORMAT I (canadasport) ────────────────────────────────────────────────────

function dsgParseFormatI(array $items, string $category): array {
    $events = []; $unmatched = [];
    $group  = DSG_CATEGORY_LABELS[$category] ?? $category;
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events'=>[], 'unmatched'=>[]];

    $currentGroup = $group;
    foreach ($items as $item) {
        $text = $item['text'];
        if ($item['bold']) { $currentGroup = $text; continue; }

        // "Flo Hockey 002: Kindersley vs Estevan (Home) | 2026-01-21 08:00 PM EST"
        if (preg_match('/^(.+?)\s+(\d{3}):\s*(.+?)\s*\|\s*(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})\s*(AM|PM)?\s*([A-Z]{2,5})?$/i', $text, $m)) {
            $channel  = trim($m[1]) . ' ' . $m[2];
            $eName    = trim($m[3]);
            $dateStr  = $m[4];
            $timeStr  = trim($m[5] . ' ' . ($m[6] ?? ''));
            $tzStr    = $m[7] ?? 'UTC';
            $tzMap    = ['EST'=>'America/New_York','EDT'=>'America/New_York','CST'=>'America/Chicago',
                         'MST'=>'America/Denver','PST'=>'America/Los_Angeles','PDT'=>'America/Los_Angeles',
                         'UTC'=>'UTC'];
            $tz       = $tzMap[strtoupper($tzStr)] ?? 'UTC';
            $startUtc = dsgToUtc($dateStr, $timeStr, $tz);
            $events[] = dsgEvent($category, $currentGroup, $eName, $startUtc, null, [$channel], 'I');
        } elseif (mb_strlen($text) > 5 && !preg_match('/^[\-–=]+$/', $text)) {
            $unmatched[] = $text;
        }
    }
    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── FORMAT J (sportreplays) ────────────────────────────────────────────────────

function dsgParseFormatJ(array $items, string $category): array {
    $events = []; $unmatched = [];
    $group  = DSG_CATEGORY_LABELS[$category] ?? $category;
    $allText = implode(' ', array_column($items, 'text'));
    if (stripos($allText, 'no schedule') !== false) return ['events'=>[], 'unmatched'=>[]];

    $currentGroup = $group;
    $pendingName  = null;
    $pendingChs   = [];
    $replayDate   = null;

    $flush = function() use (&$events, &$pendingName, &$pendingChs, &$replayDate,
                              &$currentGroup, $category) {
        if ($pendingName === null) return;
        // Sportreplays: no start time, mark as replay
        $events[] = dsgEvent($category, $currentGroup, $pendingName, null, null, $pendingChs, 'J', true);
        $pendingName = null; $pendingChs = []; $replayDate = null;
    };

    foreach ($items as $item) {
        $text = $item['text'];
        // Emoji header: ⚽️ Title ⚽️
        if (preg_match('/^\p{So}\p{Emoji_Modifier_Base}?.*\p{So}/u', $text) || preg_match('/⚽|🏀|🎾|🏈|🏉|🏒|⚾/u', $text)) {
            $flush();
            $currentGroup = trim(preg_replace('/[\x{1F300}-\x{1F9FF}\x{2600}-\x{26FF}\x{2700}-\x{27BF}️\s]+/u', ' ', $text));
            continue;
        }
        // Separator
        if (preg_match('/^[\-–—=]{2,}$/', $text)) { $flush(); continue; }
        // "Ready Replay From now to DD.MM.YYYY"
        if (stripos($text, 'Replay') !== false) { continue; }
        // Bold = match name
        if ($item['bold'] && mb_strlen($text) > 3) {
            if ($pendingName !== null) $flush();
            $pendingName = $text; $pendingChs = [];
            continue;
        }
        // li = availability note
        if ($item['tag'] === 'li') { continue; }
        // If pending, accumulate as channel name
        if ($pendingName !== null && mb_strlen($text) > 2) {
            $pendingChs[] = $text;
        }
    }
    $flush();
    return ['events' => $events, 'unmatched' => $unmatched];
}

// ── DB UPSERT ─────────────────────────────────────────────────────────────────

function dsgUpsertEvents(PDO $db, array $events, int $runId): array {
    $ins = $db->prepare("
        INSERT INTO sports_listings
            (sport_category, sport_logo_url, event_name, time_uk, time_et, channels,
             event_date, source_category, group_name, start_utc, end_utc,
             content_hash, format_matched, is_replay, scraped_run_id, channel_group_hint,
             last_scraped_date)
        VALUES
            (?, '', ?, ?, '', ?,
             ?, ?, ?, ?, ?,
             ?, ?, ?, ?, ?,
             CURDATE())
        ON DUPLICATE KEY UPDATE
            sport_category     = VALUES(sport_category),
            event_name         = VALUES(event_name),
            time_uk            = VALUES(time_uk),
            channels           = VALUES(channels),
            event_date         = VALUES(event_date),
            source_category    = VALUES(source_category),
            group_name         = VALUES(group_name),
            start_utc          = VALUES(start_utc),
            end_utc            = VALUES(end_utc),
            format_matched     = VALUES(format_matched),
            is_replay          = VALUES(is_replay),
            scraped_run_id     = VALUES(scraped_run_id),
            channel_group_hint = VALUES(channel_group_hint),
            last_scraped_date  = CURDATE()
    ");

    $inserted = 0; $stale = 0;
    foreach ($events as $ev) {
        if (!dsgIsFresh($ev['start_utc'])) { $stale++; continue; }

        $ins->execute([
            $ev['sport_category'],
            $ev['event_name'],
            $ev['time_uk'],
            json_encode($ev['channels'], JSON_UNESCAPED_UNICODE),
            $ev['event_date'],
            $ev['source_category'],
            $ev['group_name'],
            $ev['start_utc'],
            $ev['end_utc'],
            $ev['content_hash'],
            $ev['format_matched'],
            $ev['is_replay'],
            $runId,
            $ev['channel_group_hint'] ?? null,
        ]);
        $inserted++;
    }
    return ['inserted' => $inserted, 'stale' => $stale];
}

// ── MAIN ENTRY POINT ──────────────────────────────────────────────────────────

function runSportsScrape(bool $verbose = false, int $requestDelayUs = -1): array {
    @set_time_limit(300);
    // In web context use 100ms between requests so the scrape fits in ~15s instead of ~30s.
    if ($requestDelayUs < 0) {
        $requestDelayUs = (php_sapi_name() === 'cli') ? DSG_REQUEST_DELAY_US : 100000;
    }
    $log    = [];
    $dryRun = false;
    $singleCat = null;

    // CLI argument parsing
    if (php_sapi_name() === 'cli') {
        $opts = getopt('', ['dry-run', 'category:']);
        $dryRun    = isset($opts['dry-run']);
        $singleCat = $opts['category'] ?? null;
    }

    $log[] = date('[Y-m-d H:i:s]') . " Sports scrape starting" . ($dryRun ? ' [DRY RUN]' : '');

    try {
        $db = db();
        dsgEnsureSchema($db);

        $runId   = time();
        $configs = dsgLoadPageConfig($db);
        if ($singleCat) {
            $configs = array_values(array_filter($configs, fn($c) => $c['category'] === $singleCat));
        }

        $totalInserted = 0;
        $totalStale    = 0;
        $totalUnmatched= 0;
        $catOk = 0; $catFail = 0;
        $seenHashes = [];

        foreach ($configs as $cfg) {
            $cat  = $cfg['category'];
            $slug = $cfg['slug'];
            $url  = DSG_BASE_URL . $slug;
            $log[] = date('[Y-m-d H:i:s]') . " Fetching /$slug";
            try {
                $html  = dsgFetchHtml($url);
                $items = dsgExtractItems($html);

                if (empty($items)) {
                    $log[] = "  /$cat: no content extracted (possible layout change)";
                    $catFail++;
                    continue;
                }

                $result   = dsgParseItems($items, $cat, $cfg['tz'] ?? 'Europe/London', $cfg['parser_format'] ?? null);
                $events   = $result['events']   ?? [];
                $unmatched= $result['unmatched'] ?? [];

                // Deduplicate within this run
                $events = array_filter($events, function($ev) use (&$seenHashes) {
                    if (isset($seenHashes[$ev['content_hash']])) return false;
                    $seenHashes[$ev['content_hash']] = true;
                    return true;
                });
                $events = array_values($events);

                $log[] = "  /$cat: " . count($events) . " events, " . count($unmatched) . " unmatched";

                if (!$dryRun && !empty($events)) {
                    $res = dsgUpsertEvents($db, $events, $runId);
                    $totalInserted += $res['inserted'];
                    $totalStale    += $res['stale'];
                    $log[] = "  /$cat: inserted={$res['inserted']}, stale_discarded={$res['stale']}";
                }

                if (!$dryRun && !empty($unmatched)) {
                    $insU = $db->prepare("INSERT INTO sports_unmatched_blocks (category, raw_text) VALUES (?, ?)");
                    foreach (array_slice($unmatched, 0, 20) as $raw) {
                        $insU->execute([$cat, mb_substr($raw, 0, 2000)]);
                    }
                }
                $totalUnmatched += count($unmatched);
                $catOk++;

            } catch (Throwable $e) {
                $log[] = "  /$cat ERROR: " . $e->getMessage();
                $catFail++;
            }

            if (!$dryRun && $cfg !== end($configs)) {
                usleep($requestDelayUs);
            }
        }

        if (!$dryRun) {
            // Remove events from previous runs that were not refreshed (vanished from site)
            $db->prepare("DELETE FROM sports_listings WHERE scraped_run_id < ? AND scraped_run_id IS NOT NULL")
               ->execute([$runId]);
            // Remove old unmatched block logs
            $db->exec("DELETE FROM sports_unmatched_blocks WHERE logged_at < DATE_SUB(NOW(), INTERVAL 7 DAY)");
            // Remove very old listings
            $db->exec("DELETE FROM sports_listings WHERE event_date < DATE_SUB(CURDATE(), INTERVAL 14 DAY)");
        }

        $log[] = date('[Y-m-d H:i:s]') . " Done: inserted=$totalInserted, stale=$totalStale, unmatched=$totalUnmatched, cats_ok=$catOk, cats_fail=$catFail";

        // Scrape log entry
        if (!$dryRun) {
            try {
                $db->prepare("
                    INSERT INTO sports_scrape_log
                        (events_inserted, success, categories_scraped, categories_failed, stale_discarded, unmatched_logged)
                    VALUES (?, 1, ?, ?, ?, ?)
                ")->execute([$totalInserted, $catOk, $catFail, $totalStale, $totalUnmatched]);
                $db->exec("DELETE FROM sports_scrape_log WHERE ran_at < DATE_SUB(NOW(), INTERVAL 90 DAY)");
            } catch (Throwable $le) {}
        }

    } catch (Throwable $e) {
        $log[] = date('[Y-m-d H:i:s]') . " FATAL: " . $e->getMessage();
        try {
            $db2 = db();
            $db2->prepare("INSERT INTO sports_scrape_log (events_inserted, success, error_msg) VALUES (0, 0, ?)")
                ->execute([$e->getMessage()]);
        } catch (Throwable $le) {}
    }

    return $log;
}

// CLI entry point
if (php_sapi_name() === 'cli' || basename($_SERVER['SCRIPT_FILENAME'] ?? '') === 'scrape_sports.php') {
    $lines = runSportsScrape(false);
    foreach ($lines as $line) echo $line . "\n";
}
