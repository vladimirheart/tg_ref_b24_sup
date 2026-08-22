param()

$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path

$photoServicePath = Join-Path $root 'spring-panel\src\main\java\com\example\panel\service\PanelUserPhotoService.java'
$maxConfigPath = Join-Path $root 'java-bot\bot-max\src\main\resources\application.yml'

foreach ($path in @($photoServicePath, $maxConfigPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required file not found: $path"
    }
}

function Read-TextFile {
    param([string]$Path)

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and
        $bytes[0] -eq 0xEF -and
        $bytes[1] -eq 0xBB -and
        $bytes[2] -eq 0xBF

    return @{
        Text = [System.IO.File]::ReadAllText($Path)
        HasBom = $hasBom
    }
}

function Normalize-Lf {
    param([string]$Text)

    $normalized = $Text.Replace("`r`r`n", "`n")
    $normalized = $normalized.Replace("`r`n", "`n")
    $normalized = $normalized.Replace("`r", "`n")
    return $normalized
}

function Write-Utf8Lf {
    param(
        [string]$Path,
        [string]$Text,
        [bool]$HasBom
    )

    $normalized = Normalize-Lf -Text $Text
    $normalized = $normalized.TrimEnd([char[]]"`n") + "`n"

    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $HasBom
    [System.IO.File]::WriteAllText($Path, $normalized, $encoding)
}

function Replace-Exactly {
    param(
        [string]$Text,
        [string]$Old,
        [string]$New,
        [string]$Label,
        [int]$ExpectedCount = 1
    )

    # The repository files are normalized to LF, while Windows PowerShell
    # here-strings use CRLF. Normalize all operands before exact matching.
    $normalizedText = Normalize-Lf -Text $Text
    $normalizedOld = Normalize-Lf -Text $Old
    $normalizedNew = Normalize-Lf -Text $New

    $count = ([regex]::Matches(
        $normalizedText,
        [regex]::Escape($normalizedOld)
    )).Count

    if ($count -ne $ExpectedCount) {
        throw "${Label}: expected exactly $ExpectedCount match(es), found $count"
    }

    return $normalizedText.Replace($normalizedOld, $normalizedNew)
}

$photoFile = Read-TextFile -Path $photoServicePath
$maxFile = Read-TextFile -Path $maxConfigPath

$photo = Normalize-Lf -Text $photoFile.Text
$maxConfig = Normalize-Lf -Text $maxFile.Text

# ---------------------------------------------------------------------------
# Panel user avatar lazy migration.
# Keep the public constructor unchanged so existing tests/manual creation remain valid.
# Spring injects the legacy local avatar root through a small config method.
# ---------------------------------------------------------------------------

$oldImports = @'
import com.example.panel.storage.AttachmentObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
'@

$newImports = @'
import com.example.panel.storage.AttachmentObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
'@

$photo = Replace-Exactly `
    -Text $photo `
    -Old $oldImports `
    -New $newImports `
    -Label 'PanelUserPhotoService imports'

$oldFields = @'
    private final AttachmentObjectStorageService attachmentObjectStorageService;

    public PanelUserPhotoService(AttachmentObjectStorageService attachmentObjectStorageService) {
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }
'@

$newFields = @'
    private final AttachmentObjectStorageService attachmentObjectStorageService;
    private Path legacyLocalAvatarsRoot = Paths.get("attachments/avatars").toAbsolutePath().normalize();

    public PanelUserPhotoService(AttachmentObjectStorageService attachmentObjectStorageService) {
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }

    @Value("${app.storage.avatars:attachments/avatars}")
    void configureLegacyLocalAvatarsRoot(String avatarsDir) {
        if (StringUtils.hasText(avatarsDir)) {
            this.legacyLocalAvatarsRoot = Paths.get(avatarsDir).toAbsolutePath().normalize();
        }
    }
'@

$photo = Replace-Exactly `
    -Text $photo `
    -Old $oldFields `
    -New $newFields `
    -Label 'PanelUserPhotoService legacy root config'

$oldStoredResolver = @'
    private String resolveStoredAvatarPath(String rawFilename) {
        String filename = extractFilename(rawFilename);
        if (!StringUtils.hasText(filename) || !avatarExists(filename)) {
            return null;
        }
        return buildStoredAvatarUrl(filename);
    }
'@

$newStoredResolver = @'
    private String resolveStoredAvatarPath(String rawFilename) {
        String filename = extractFilename(rawFilename);
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        if (!avatarExists(filename) && !migrateLegacyLocalAvatar(filename)) {
            return null;
        }
        return buildStoredAvatarUrl(filename);
    }
'@

$photo = Replace-Exactly `
    -Text $photo `
    -Old $oldStoredResolver `
    -New $newStoredResolver `
    -Label 'Stored avatar resolver'

$oldLegacyResolver = @'
    private String resolveLegacyUserPhotoPath(String value) {
        String filename = extractFilename(value);
        if (StringUtils.hasText(filename) && avatarExists(filename)) {
            return buildStoredAvatarUrl(filename);
        }
        return value.startsWith("/") ? value : "/" + value;
    }
'@

$newLegacyResolver = @'
    private String resolveLegacyUserPhotoPath(String value) {
        String filename = extractFilename(value);
        if (StringUtils.hasText(filename)
                && (avatarExists(filename) || migrateLegacyLocalAvatar(filename))) {
            return buildStoredAvatarUrl(filename);
        }
        return value.startsWith("/") ? value : "/" + value;
    }
'@

$photo = Replace-Exactly `
    -Text $photo `
    -Old $oldLegacyResolver `
    -New $newLegacyResolver `
    -Label 'Legacy avatar resolver'

$avatarExistsAnchor = @'
    private boolean avatarExists(String filename) {
        return attachmentObjectStorageService.avatarExists(filename);
    }
'@

$migrationBlock = @'
    private boolean avatarExists(String filename) {
        return attachmentObjectStorageService.avatarExists(filename);
    }

    private boolean migrateLegacyLocalAvatar(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        Path source = legacyLocalAvatarsRoot.resolve(filename).normalize();
        if (!source.startsWith(legacyLocalAvatarsRoot) || !Files.isRegularFile(source)) {
            return false;
        }
        try (InputStream inputStream = Files.newInputStream(source)) {
            attachmentObjectStorageService
                    .storeAvatar(filename, Files.probeContentType(source), inputStream)
                    .close();
            return avatarExists(filename);
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }
'@

$photo = Replace-Exactly `
    -Text $photo `
    -Old $avatarExistsAnchor `
    -New $migrationBlock `
    -Label 'Avatar lazy migration helper'

# ---------------------------------------------------------------------------
# Remove obsolete v21 MAX-local logging override.
# The panel runtime contract now owns APP_BOT_LOG_PATH per channel.
# ---------------------------------------------------------------------------

$loggingBlock = @'

# MAX-specific log identity; shared Logback falls back to bot-telegram.log otherwise.
logging:
  file:
    name: logs/bot-max.log
'@
$loggingBlock = Normalize-Lf -Text $loggingBlock

if ($maxConfig.Contains($loggingBlock)) {
    $maxConfig = $maxConfig.Replace($loggingBlock, '')
}

if (-not $maxConfig.Contains('# Iguana MAX launcher config v13')) {
    throw 'Expected MAX v13 launcher marker was not found.'
}

Write-Utf8Lf -Path $photoServicePath -Text $photo -HasBom ([bool]$photoFile.HasBom)
Write-Utf8Lf -Path $maxConfigPath -Text $maxConfig -HasBom ([bool]$maxFile.HasBom)

Write-Host ''
Write-Host 'Panel user avatar storage fix v21.4 applied.'
Write-Host '  Legacy local panel-user avatars lazily migrate into canonical avatar storage.'
Write-Host '  Sidebar and Dialogs operator avatar use the same restored URL automatically.'
Write-Host '  Existing PanelUserPhotoService constructor remains compatible with tests.'
Write-Host '  Obsolete MAX-local logging override removed.'
Write-Host ''
Write-Host 'Next:'
Write-Host '  cd .\spring-panel'
Write-Host '  .\mvnw.cmd -q -DskipTests compile'
Write-Host '  .\mvnw.cmd -q -DskipTests testCompile'
Write-Host '  cd ..'
Write-Host '  git diff --check'
Write-Host '  git status --short'
Write-Host ''
Write-Host 'After panel restart, hard-refresh Sidebar and Dialogs.'
