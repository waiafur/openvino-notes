#!/usr/bin/env python3
"""Prepare YOLO26n OpenVINO IR assets for the Android AI module."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


MODEL_NAME = "yolo26n"
MODEL_PT = f"{MODEL_NAME}.pt"
MODEL_DIR = f"{MODEL_NAME}_openvino_model"
MODEL_FILES = (f"{MODEL_NAME}.xml", f"{MODEL_NAME}.bin", "metadata.yaml")


def run(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def venv_python(venv_dir: Path) -> Path:
    if os.name == "nt":
        return venv_dir / "Scripts" / "python.exe"
    return venv_dir / "bin" / "python"


def ensure_venv(venv_dir: Path) -> Path:
    python = venv_python(venv_dir)
    if not python.exists():
        run([sys.executable, "-m", "venv", str(venv_dir)])
        run([str(python), "-m", "pip", "install", "--upgrade", "pip"])
    return python


def ensure_python_packages(python: Path) -> None:
    check_code = "import ultralytics, openvino"
    result = subprocess.run([str(python), "-c", check_code], check=False)
    if result.returncode == 0:
        return

    run([str(python), "-m", "pip", "install", "-U", "ultralytics", "openvino"])


def export_model(python: Path, work_dir: Path) -> Path:
    export_code = f"""
from ultralytics import YOLO

model = YOLO("{MODEL_PT}")
model.export(format="openvino", imgsz=640, batch=1, dynamic=False, end2end=True)
"""
    run([str(python), "-c", export_code], cwd=work_dir)

    model_dir = work_dir / MODEL_DIR
    missing = [name for name in MODEL_FILES if not (model_dir / name).exists()]
    if missing:
        raise FileNotFoundError(f"Ultralytics export did not produce expected files: {missing}")
    return model_dir


def write_coco_names(python: Path, work_dir: Path, output_file: Path) -> None:
    names_code = f"""
from pathlib import Path
from ultralytics import YOLO

model = YOLO("{MODEL_PT}")
names = model.names
Path(r"{output_file}").write_text(
    "\\n".join(names[i] for i in range(len(names))),
    encoding="utf-8",
)
"""
    run([str(python), "-c", names_code], cwd=work_dir)


def verify_openvino_model(python: Path, model_xml: Path) -> None:
    verify_code = f"""
from openvino import Core

core = Core()
model = core.read_model(r"{model_xml}")
compiled = core.compile_model(model, "CPU")
print("Prepared OpenVINO model")
for inp in compiled.inputs:
    print("input:", inp.shape, inp.element_type)
for out in compiled.outputs:
    print("output:", out.shape, out.element_type)
"""
    run([str(python), "-c", verify_code])


def copy_assets(model_dir: Path, output_assets_dir: Path, python: Path, work_dir: Path) -> None:
    output_model_dir = output_assets_dir / "models" / MODEL_DIR
    output_model_dir.mkdir(parents=True, exist_ok=True)

    for name in MODEL_FILES:
        shutil.copy2(model_dir / name, output_model_dir / name)

    write_coco_names(python, work_dir, output_assets_dir / "coco.names")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-assets-dir", required=True, type=Path)
    parser.add_argument("--work-dir", required=True, type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.output_assets_dir.mkdir(parents=True, exist_ok=True)
    args.work_dir.mkdir(parents=True, exist_ok=True)

    python = ensure_venv(args.work_dir / ".venv")
    ensure_python_packages(python)
    model_dir = export_model(python, args.work_dir)
    copy_assets(model_dir, args.output_assets_dir, python, args.work_dir)
    verify_openvino_model(python, args.output_assets_dir / "models" / MODEL_DIR / f"{MODEL_NAME}.xml")


if __name__ == "__main__":
    main()
