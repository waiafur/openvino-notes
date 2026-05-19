#!/usr/bin/env python3
"""Prepare YOLO OpenVINO IR assets for the Android AI module."""
from __future__ import annotations
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

# Список моделей для подготовки
MODELS = [
    {
        "name": "yolo26n",
        "pt": "yolo26n.pt",
        "dir": "yolo26n_openvino_model",
        "files": ("yolo26n.xml", "yolo26n.bin", "metadata.yaml"),
        "export_args": {"end2end": True}
    },
    {
        "name": "yolov10n",
        "pt": "yolov10n.pt",
        "dir": "yolov10n_openvino_model",
        "files": ("yolov10n.xml", "yolov10n.bin", "metadata.yaml"),
        "export_args": {}
    }
]

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

def export_model(python: Path, work_dir: Path, model: dict) -> Path:
    """Экспорт модели в OpenVINO"""

    # Формируем аргументы экспорта
    export_args_str = ", ".join([f"{k}={v}" for k, v in model["export_args"].items()])
    if export_args_str:
        export_args_str = ", " + export_args_str

    export_code = f"""
from ultralytics import YOLO
model = YOLO("{model['pt']}")
model.export(format="openvino", imgsz=640, batch=1, dynamic=False{export_args_str})
"""
    run([str(python), "-c", export_code], cwd=work_dir)

    model_dir = work_dir / model["dir"]
    missing = [name for name in model["files"] if not (model_dir / name).exists()]
    if missing:
        raise FileNotFoundError(f"Ultralytics export did not produce expected files for {model['name']}: {missing}")
    return model_dir

def write_coco_names(python: Path, work_dir: Path, output_file: Path) -> None:
    """Записываем coco.names (используем первую модель для получения имён)"""
    first_model = MODELS[0]
    names_code = f"""
from pathlib import Path
from ultralytics import YOLO
model = YOLO("{first_model['pt']}")
names = model.names
Path(r"{output_file}").write_text(
    "\\n".join(names[i] for i in range(len(names))),
    encoding="utf-8",
)
"""
    run([str(python), "-c", names_code], cwd=work_dir)

def verify_openvino_model(python: Path, model_xml: Path, model_name: str) -> None:
    verify_code = f"""
from openvino import Core
core = Core()
model = core.read_model(r"{model_xml}")
compiled = core.compile_model(model, "CPU")
print("Prepared OpenVINO model for {model_name}")
for inp in compiled.inputs:
    print("input:", inp.shape, inp.element_type)
for out in compiled.outputs:
    print("output:", out.shape, out.element_type)
"""
    run([str(python), "-c", verify_code])

def copy_assets(model_dir: Path, output_assets_dir: Path, model: dict) -> None:
    output_model_dir = output_assets_dir / "models" / model["dir"]
    output_model_dir.mkdir(parents=True, exist_ok=True)
    for name in model["files"]:
        shutil.copy2(model_dir / name, output_model_dir / name)

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-assets-dir", required=True, type=Path)
    parser.add_argument("--work-dir", required=True, type=Path)
    return parser.parse_args()

def main() -> None:
    args = parse_args()
    args.output_assets_dir.mkdir(parents=True, exist_ok=True)
    args.work_dir.mkdir(parents=True,exist_ok=True)

    python = ensure_venv(args.work_dir / ".venv")
    ensure_python_packages(python)

    # Записываем coco.names один раз
    write_coco_names(python, args.work_dir, args.output_assets_dir / "coco.names")

    # Подготавливаем каждую модель
    for model in MODELS:
        print(f"\n{'='*50}")
        print(f"Preparing {model['name']}...")
        print(f"{'='*50}")

        model_dir = export_model(python, args.work_dir, model)
        copy_assets(model_dir, args.output_assets_dir, model)
        verify_openvino_model(python, args.output_assets_dir / "models" / model["dir"] / f"{model['name']}.xml", model['name'])

        print(f"[OK] {model['name']} done!")

if __name__ == "__main__":
    main()
