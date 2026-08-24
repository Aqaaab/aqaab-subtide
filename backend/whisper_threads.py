"""Resource limits for Whisper on low-memory machines.

The limits are environment-configurable and intentionally conservative for
4GB RAM systems. This module is imported before Whisper model inference.
"""
import os


def configure_whisper_threads(default: int = 2) -> int:
    """Configure Torch/BLAS CPU parallelism and return the selected thread count."""
    raw = os.getenv("WHISPER_CPU_THREADS", str(default))
    try:
        requested = int(raw)
    except (TypeError, ValueError):
        requested = default

    available = os.cpu_count() or default
    threads = max(1, min(requested, available, 2))

    # Set common BLAS/OpenMP limits before importing/initializing heavy models.
    os.environ["OMP_NUM_THREADS"] = str(threads)
    os.environ["MKL_NUM_THREADS"] = str(threads)
    os.environ["OPENBLAS_NUM_THREADS"] = str(threads)
    os.environ["NUMEXPR_NUM_THREADS"] = str(threads)
    os.environ["TOKENIZERS_PARALLELISM"] = "false"

    try:
        import torch
        torch.set_num_threads(threads)
        try:
            torch.set_num_interop_threads(min(threads, 2))
        except RuntimeError:
            # Torch may already have initialized its inter-op pool.
            pass
    except ImportError:
        pass

    return threads
