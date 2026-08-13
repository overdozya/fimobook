import json
import subprocess
from pathlib import Path

# 맥 클립보드 내용 가져오기
clipboard = subprocess.run(
    ["pbpaste"],
    capture_output=True,
    text=True
).stdout

data = json.loads(clipboard)

new_players = data["ResultData"]["PlayerList"]

output_path = Path(__file__).resolve().parent.parent / "src/data/players.json"

if output_path.exists():
    with open(output_path, "r", encoding="utf-8") as f:
        existing_players = json.load(f)
else:
    existing_players = []

# cid 기준 중복 제거
player_map = {
    player["cid"]: player
    for player in existing_players
}

for player in new_players:
    player_map[player["cid"]] = player

merged_players = list(player_map.values())

with open(output_path, "w", encoding="utf-8") as f:
    json.dump(
        merged_players,
        f,
        ensure_ascii=False,
        indent=2
    )

print(f"추가 완료: {len(new_players)}장")
print(f"현재 총 카드: {len(merged_players)}장")

print("클립보드에서 읽은 카드 수:", len(new_players))

for p in new_players[:3]:
    print(p["cid"], p["playerKor"])

print(f"이번 응답 카드: {len(new_players)}장")
print(f"현재 players.json 총 카드: {len(merged_players)}장")
print("저장 위치:", output_path)

print("실제 저장 경로:", output_path.resolve())