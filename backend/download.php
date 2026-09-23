<?php
require_once __DIR__ . '/includes/db.php';

$apk_url = 'nexStream.apk';
$version = '1.0.4 (Build 0320)';

// Count each download.php page load as an APK download
try {
    $db = db();
    $db->exec("CREATE TABLE IF NOT EXISTS site_stats (
        stat_date DATE NOT NULL,
        page_views INT UNSIGNED NOT NULL DEFAULT 0,
        apk_downloads INT UNSIGNED NOT NULL DEFAULT 0,
        PRIMARY KEY (stat_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $db->prepare("INSERT INTO site_stats (stat_date, apk_downloads) VALUES (CURDATE(), 1)
        ON DUPLICATE KEY UPDATE apk_downloads = apk_downloads + 1")->execute();
} catch (Exception $e) { /* silent */ }
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>nexStream — Download</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Exo+2:wght@300;400;600;800&family=Inter:wght@300;400;500&display=swap" rel="stylesheet">
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            --bg:        #04080f;
            --surface:   #0a1220;
            --border:    #1a2840;
            --blue:      #0365c7;
            --blue-glow: rgba(3, 101, 199, 0.35);
            --yellow:    #fcce0c;
            --text:      #ffffff;
            --muted:     #7a9ab8;
            --success:   #22c55e;
        }

        html, body {
            height: 100%;
            background: var(--bg);
            color: var(--text);
            font-family: 'Inter', sans-serif;
            overflow-x: hidden;
        }

        /* ── Animated background grid ── */
        body::before {
            content: '';
            position: fixed;
            inset: 0;
            background-image:
                linear-gradient(rgba(3,101,199,0.06) 1px, transparent 1px),
                linear-gradient(90deg, rgba(3,101,199,0.06) 1px, transparent 1px);
            background-size: 48px 48px;
            animation: gridMove 20s linear infinite;
            pointer-events: none;
            z-index: 0;
        }

        @keyframes gridMove {
            0%   { transform: translateY(0); }
            100% { transform: translateY(48px); }
        }

        /* ── Glow orb ── */
        body::after {
            content: '';
            position: fixed;
            top: -200px;
            left: 50%;
            transform: translateX(-50%);
            width: 700px;
            height: 500px;
            background: radial-gradient(ellipse, rgba(3,101,199,0.18) 0%, transparent 70%);
            pointer-events: none;
            z-index: 0;
        }

        .page {
            position: relative;
            z-index: 1;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px 20px;
            gap: 48px;
        }

        /* ── Logo ── */
        .logo {
            display: flex;
            align-items: center;
            gap: 14px;
            animation: fadeDown 0.6s ease both;
        }

        .logo-mark {
            width: 52px;
            height: 52px;
            background: linear-gradient(135deg, var(--blue), #0284c7);
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Exo 2', sans-serif;
            font-weight: 800;
            font-size: 22px;
            color: #fff;
            box-shadow: 0 0 30px var(--blue-glow);
            letter-spacing: -1px;
        }

        .logo-name {
            font-family: 'Exo 2', sans-serif;
            font-weight: 800;
            font-size: 32px;
            letter-spacing: -0.5px;
            background: linear-gradient(90deg, #fff 60%, var(--blue));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        /* ── Card ── */
        .card {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 20px;
            padding: 48px 40px;
            width: 100%;
            max-width: 520px;
            text-align: center;
            animation: fadeUp 0.7s ease 0.1s both;
            box-shadow: 0 0 60px rgba(3,101,199,0.08), 0 24px 48px rgba(0,0,0,0.5);
        }

        /* ── Download animation ── */
        .download-icon {
            width: 72px;
            height: 72px;
            margin: 0 auto 24px;
            position: relative;
        }

        .download-icon svg {
            width: 100%;
            height: 100%;
        }

        .arrow-path {
            stroke-dasharray: 100;
            stroke-dashoffset: 100;
            animation: drawArrow 0.8s ease 0.4s forwards;
        }

        .bar-path {
            stroke-dasharray: 60;
            stroke-dashoffset: 60;
            animation: drawBar 0.5s ease 1s forwards;
        }

        @keyframes drawArrow {
            to { stroke-dashoffset: 0; }
        }

        @keyframes drawBar {
            to { stroke-dashoffset: 0; }
        }

        .pulse-ring {
            position: absolute;
            inset: -8px;
            border-radius: 50%;
            border: 2px solid var(--blue);
            opacity: 0;
            animation: pulseRing 2s ease 1.2s infinite;
        }

        @keyframes pulseRing {
            0%   { transform: scale(0.85); opacity: 0.6; }
            100% { transform: scale(1.3);  opacity: 0; }
        }

        .status-text {
            font-family: 'Exo 2', sans-serif;
            font-size: 22px;
            font-weight: 700;
            color: var(--text);
            margin-bottom: 8px;
        }

        .status-sub {
            font-size: 14px;
            color: var(--muted);
            margin-bottom: 32px;
        }

        /* ── Progress bar ── */
        .progress-wrap {
            background: var(--border);
            border-radius: 99px;
            height: 4px;
            margin-bottom: 36px;
            overflow: hidden;
        }

        .progress-bar {
            height: 100%;
            border-radius: 99px;
            background: linear-gradient(90deg, var(--blue), var(--yellow));
            width: 0%;
            animation: fillBar 2.5s cubic-bezier(0.4,0,0.2,1) 0.5s forwards;
        }

        @keyframes fillBar {
            0%   { width: 0%; }
            60%  { width: 85%; }
            100% { width: 100%; }
        }

        /* ── Steps ── */
        .steps-title {
            font-size: 11px;
            font-weight: 500;
            letter-spacing: 1.5px;
            text-transform: uppercase;
            color: var(--muted);
            margin-bottom: 16px;
            text-align: left;
        }

        .steps {
            display: flex;
            flex-direction: column;
            gap: 12px;
            text-align: left;
        }

        .step {
            display: flex;
            align-items: flex-start;
            gap: 14px;
            padding: 14px 16px;
            background: rgba(255,255,255,0.03);
            border: 1px solid var(--border);
            border-radius: 10px;
            opacity: 0;
            transform: translateX(-8px);
        }

        .step:nth-child(1) { animation: slideIn 0.4s ease 1.5s forwards; }
        .step:nth-child(2) { animation: slideIn 0.4s ease 1.7s forwards; }
        .step:nth-child(3) { animation: slideIn 0.4s ease 1.9s forwards; }
        .step:nth-child(4) { animation: slideIn 0.4s ease 2.1s forwards; }

        @keyframes slideIn {
            to { opacity: 1; transform: translateX(0); }
        }

        .step-num {
            width: 24px;
            height: 24px;
            min-width: 24px;
            border-radius: 50%;
            background: var(--blue);
            color: #fff;
            font-size: 12px;
            font-weight: 700;
            display: flex;
            align-items: center;
            justify-content: center;
            margin-top: 1px;
        }

        .step-content strong {
            display: block;
            font-size: 13px;
            font-weight: 600;
            color: var(--text);
            margin-bottom: 2px;
        }

        .step-content span {
            font-size: 12px;
            color: var(--muted);
            line-height: 1.5;
        }

        .step-content .highlight {
            color: var(--yellow);
            font-weight: 500;
        }

        /* ── Manual download link ── */
        .manual-link {
            margin-top: 28px;
            font-size: 13px;
            color: var(--muted);
            animation: fadeUp 0.5s ease 2.5s both;
        }

        .manual-link a {
            color: var(--blue);
            text-decoration: none;
            font-weight: 500;
            border-bottom: 1px solid transparent;
            transition: border-color 0.2s;
        }

        .manual-link a:hover {
            border-color: var(--blue);
        }

        /* ── Footer ── */
        .footer {
            font-size: 12px;
            color: var(--muted);
            text-align: center;
            animation: fadeUp 0.5s ease 0.3s both;
        }

        .footer a {
            color: var(--muted);
            text-decoration: none;
            transition: color 0.2s;
        }

        .footer a:hover { color: var(--text); }

        /* ── Animations ── */
        @keyframes fadeDown {
            from { opacity: 0; transform: translateY(-16px); }
            to   { opacity: 1; transform: translateY(0); }
        }

        @keyframes fadeUp {
            from { opacity: 0; transform: translateY(16px); }
            to   { opacity: 1; transform: translateY(0); }
        }

        @media (max-width: 560px) {
            .card { padding: 36px 24px; }
            .logo-name { font-size: 26px; }
        }
    </style>
</head>
<body>
    <div class="page">

        <div class="logo">
            <div class="logo-mark">N</div>
            <div class="logo-name">nexStream</div>
        </div>

        <div class="card">
            <div class="download-icon">
                <div class="pulse-ring"></div>
                <svg viewBox="0 0 72 72" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="36" cy="36" r="35" stroke="#0365c7" stroke-width="1.5" opacity="0.3"/>
                    <path class="arrow-path" d="M36 20 L36 46 M26 38 L36 48 L46 38" stroke="#0365c7" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                    <path class="bar-path" d="M24 54 L48 54" stroke="#fcce0c" stroke-width="2.5" stroke-linecap="round"/>
                </svg>
            </div>

            <div class="status-text">Your download is starting…</div>
            <div class="status-sub">nexStream v<?php echo htmlspecialchars($version); ?> for Android TV</div>

            <div class="progress-wrap">
                <div class="progress-bar"></div>
            </div>

            <div class="steps-title">Installation guide</div>
            <div class="steps">
                <div class="step">
                    <div class="step-num">1</div>
                    <div class="step-content">
                        <strong>Allow unknown sources</strong>
                        <span>On your Android TV, go to <span class="highlight">Settings → Security</span> and enable <span class="highlight">Unknown Sources</span> or <span class="highlight">Install unknown apps</span></span>
                    </div>
                </div>
                <div class="step">
                    <div class="step-num">2</div>
                    <div class="step-content">
                        <strong>Transfer the APK</strong>
                        <span>Copy the APK to a USB stick, or use a file manager app to download it directly on your TV via the browser</span>
                    </div>
                </div>
                <div class="step">
                    <div class="step-num">3</div>
                    <div class="step-content">
                        <strong>Install the APK</strong>
                        <span>Open your file manager, locate <span class="highlight">nexstream.apk</span> and select it to begin installation</span>
                    </div>
                </div>
                <div class="step">
                    <div class="step-num">4</div>
                    <div class="step-content">
                        <strong>Add your playlist</strong>
                        <span>Launch nexStream, go to <span class="highlight">Settings → Playlists</span> and add your Xtream or M3U playlist to get started</span>
                    </div>
                </div>
            </div>

            <div class="manual-link">
                Download not started? <a href="<?php echo htmlspecialchars($apk_url); ?>?manual=1" download>Click here to download manually</a>
            </div>
        </div>

        <div class="footer">
            <a href="/">← Back to nexstream.uk</a>
            &nbsp;·&nbsp;
            &copy; <?php echo date('Y'); ?> nexStream
        </div>

    </div>

    <script>
        // Auto-trigger download after short delay
        window.addEventListener('load', function () {
            setTimeout(function () {
                window.location.href = '<?php echo htmlspecialchars($apk_url); ?>';
            }, 800);
        });
    </script>
</body>
</html>