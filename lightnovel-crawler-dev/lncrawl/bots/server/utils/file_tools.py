import logging
import shlex
import subprocess
from pathlib import Path

from lncrawl.utils.platforms import Platform

logger = logging.getLogger(__name__)


def format_size(num_bytes: int, decimals: int = 1, suffix: str = "B") -> str:
    """
    Convert a size in bytes into a human-readable string representing the size,
    e.g. "117.7 MB" or "9.0 GB".

    Units go up to zettabytes (ZB), then yottabytes (YB) as a fallback.
    """
    units = ["", "K", "M", "G", "T", "P", "E", "Z"]
    size = float(num_bytes)
    for unit in units:
        if abs(size) < 1024.0:
            return f"{size:.{decimals}f} {unit}{suffix}"
        size /= 1024.0
    return f"{size:.{decimals}f} Y{suffix}"


def folder_size(folder: str) -> int:
    """
    Return the total size of a folder in bytes.

    Uses PowerShell on Windows or `du` on POSIX for speed.
    Falls back to a Python directory walk if the system command fails.
    Returns 0 if the folder does not exist or no files are readable.
    """
    try:
        if Platform.windows:
            cmd = [
                "powershell",
                "-NoProfile",
                "-Command",
                f"(Get-ChildItem -LiteralPath '{folder}' -Recurse -Force -File | Measure-Object Length -Sum).Sum",
            ]
            out = subprocess.check_output(cmd, text=True).strip()
            return int(out) if out else 0
        elif Platform.posix:
            cmd = shlex.split(f'du -s -B1 "{folder}"')
            out = subprocess.check_output(cmd, text=True).strip()
            return int(out.split()[0]) if out else 0
    except Exception:
        logger.warning("System command failed, falling back to Python scan", exc_info=True)

    # Python fallback
    try:
        return sum(
            f.stat().st_size
            for f in Path(folder).rglob("*")
            if f.is_file()
        )
    except Exception:
        logger.error("Failed to calculate folder size in Python fallback", exc_info=True)
        return 0
