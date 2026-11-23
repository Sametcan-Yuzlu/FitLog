# Draws the FitLog icons (white dumbbell on lime) as PNGs for the web app, using the same
# geometry as the Android vector icon (FitLogApp/app/src/main/res/drawable/ic_launcher_foreground.xml).
# Run from anywhere:  powershell -File web\make-icons.ps1
Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot 'icons'
New-Item -ItemType Directory -Force $out | Out-Null
$lime = [System.Drawing.Color]::FromArgb(255, 0x84, 0xCC, 0x16)

function RoundRect([System.Drawing.Drawing2D.GraphicsPath]$p, [float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    if ($r -le 0) { $p.AddRectangle((New-Object System.Drawing.RectangleF $x, $y, $w, $h)); return }
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
}

# size: output px. view: how many of the 108 icon units span the image (72 = what launchers show,
# 108 = full canvas with safe-zone padding for "maskable"). corner: rounded corners in px (0 = square).
function Draw-Icon([int]$size, [float]$view, [float]$corner, [string]$name) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.Clear([System.Drawing.Color]::Transparent)

    $bg = New-Object System.Drawing.Drawing2D.GraphicsPath
    RoundRect $bg 0 0 $size $size $corner
    $g.FillPath((New-Object System.Drawing.SolidBrush $lime), $bg)

    $s = $size / $view
    $g.TranslateTransform($size / 2, $size / 2); $g.ScaleTransform($s, $s); $g.RotateTransform(-45)
    $g.TranslateTransform(-54, -54)
    $db = New-Object System.Drawing.Drawing2D.GraphicsPath
    $db.FillMode = 'Winding'              # overlapping bar/plates fill solid instead of cancelling out
    RoundRect $db 37 51.5 34 5 0          # bar
    RoundRect $db 24 43 6 22 2.5          # left outer plate
    RoundRect $db 31 37 7 34 2.5          # left inner plate
    RoundRect $db 70 37 7 34 2.5          # right inner plate
    RoundRect $db 78 43 6 22 2.5          # right outer plate
    $g.FillPath([System.Drawing.Brushes]::White, $db)

    $g.Dispose(); $bmp.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    "wrote $name"
}

Draw-Icon 180 72 0 'apple-touch-icon.png'           # iPhone rounds the corners itself
Draw-Icon 192 72 42 'icon-192.png'
Draw-Icon 512 72 112 'icon-512.png'
Draw-Icon 512 108 0 'icon-maskable-512.png'         # Android may crop to any shape
