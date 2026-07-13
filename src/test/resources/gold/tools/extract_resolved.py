"""Pulls the "result" key (the engine's resolved DocumentResult) out of a *-debug.json export
so it can be fed to draft_from_resolved.py.

Usage: python extract_resolved.py <debug.json> <output-resolved.json>
"""
import json
import sys


def main(debug_json_path, output_path):
    debug = json.load(open(debug_json_path, encoding="utf-8"))
    json.dump(debug["result"], open(output_path, "w", encoding="utf-8"), indent=2)
    print("wrote", output_path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
