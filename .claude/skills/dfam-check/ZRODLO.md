# Źródło

Skill `dfam-check` skopiowany bez zmian (pominięto tylko `agents/openai.yaml`) z
[earthtojake/text-to-cad](https://github.com/earthtojake/text-to-cad/tree/244eecd6b675b75493fa3627292b168ffacf799d/skills/dfam-check),
commit `244eecd`, licencja MIT (plik `LICENSE`, © 2026 Thompson Labs LLC).

W projekcie DeskMate DIY:
- raport drukowalności wszystkich STL robi `hardware/tools/dfam_report.py`
  (wywołuje `scripts/dfam_tool.py`, limity FDM z `references/process-limits.md`),
- GitHub Actions uruchamia go przy każdym pushu (job `printability`), raport jest
  w podsumowaniu przebiegu i w artefakcie `dfam-report`.

Aktualizacja: skopiuj nowszy katalog `skills/dfam-check` z repo źródłowego i podmień
commit w tym pliku.
