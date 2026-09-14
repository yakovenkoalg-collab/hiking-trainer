# Доработки выполнения и записи тренировки

Включено в выпуск 0.3.17; сведения о проверках — в releases/0.3.17.md.
Согласованный план HomeRunningBlock и история пользователя не изменялись.

Проверка 14.09.2026: сборка debug и lint успешны; 51 unit-тест и 60 Android-тестов прошли на MountainFormApi35. Проверены миграция 6→7, экспорт/восстановление резервной копии, сохранение измеренных подходов, повторное завершение, повторный импорт датчика и покрытие схемами текущего плана. Обзор и выполнение просмотрены на снимках 1080×2400 со шрифтом 150%. На физическом OnePlus эта сборка ещё не проверена.

## Поведение

- «Тренируюсь сейчас» открывает выполнение; таймер этапа стартует отдельно.
- «Уже выполнил» позволяет выбрать выполненные подходы, фактическую дату, полную длительность, RPE и комментарий. Не создаёт время работы, отдыха или фактическое число повторений из плановых значений. Ранее измеренные подходы сохраняются.
- Запись занятия и выбранных подходов сохраняется транзакционно. Повторное завершение отклоняется. Экран закрывается только после успешного сохранения.
- В текущем упражнении показывается доза одного подхода: «40 сек», а не «2 × 40 сек» и не «40 повторений». Для односторонних упражнений явно указана каждая сторона.
- Названия блоков, метаданные и дозировка разделены по строкам. Подробности плана и техника раскрываются отдельно.
- У каждой записи Garmin можно вручную выбрать источник пульса: нагрудный, часы, неизвестно. Повторный импорт сохраняет выбор. Это отметка пользователя, не автоматическое определение датчика по FIT.
- Новые схемы раскрываются на весь экран; доступны масштаб ×2 и жесты масштабирования/перемещения.

## Совместимость данных

Room 6 → 7 добавляет performedEpochDay (nullable), recordingMode (UNKNOWN) и heartRateSource (UNKNOWN). Старые даты завершения и интервалы между нажатиями не интерпретируются заново и не изменяются.

Отчёт: schemaVersion 7. completedAtEpochMillis остаётся временем сохранения, performedEpochDay — подтверждённым днём занятия. RETROSPECTIVE обозначает запись после занятия; durationStatus USER_ENTERED — длительность, введённую пользователем. CHEST_USER / WRIST_USER — ручная отметка датчика.

Резервная копия: schemaVersion 4; импорт поддерживает версии 1–4. Новые поля имеют безопасные значения по умолчанию. Старое приложение не должно восстанавливать новую копию с потерей неизвестных полей.

## Иллюстрации — происхождение и финальные промпты

Инструмент: встроенный imagegen, без внешнего API/CLI. Файлы сохранены в app/src/main/res/drawable-nodpi/. Все шесть изображений проверены визуально. Они иллюстрируют упражнение, но не означают персональный допуск к нагрузке.

Общий промпт: “Use case: scientific-educational. Asset type: wide 16:9 exercise instruction illustration for Android training app. [Описание ниже] Semi-realistic clean instructional illustration of an adult athletic man in muted sage green shirt and dark trousers, neutral off-white background, soft lighting, entire body visible with generous margins. Anatomically accurate. No lettering, no numbers, no logo. All panels same person and camera. Produce ONE separate image.”

- exercise_split_squat.png: “Two side-by-side full-body poses demonstrating bodyweight stationary split squat: staggered stance both feet on floor, then lower vertically with rear knee close to floor, front knee tracks toes. No bench.”
- exercise_pull_up.png: “Two side-by-side full-body poses demonstrating strict pullup on fixed high bar: arms straight hang with feet clear of floor, then chin above bar elbows bent. Pronated grip slightly wider than shoulders. No swinging.”
- exercise_forearm_plank.png: “Single full-body side view demonstrating forearm plank: elbows directly under shoulders, forearms on mat, feet supported on toes, straight line ears shoulders hips ankles. Neutral neck, no sagging.”
- exercise_single_leg_bridge.png: “Two side-by-side full-body side views of single-leg glute bridge on mat. One supporting foot planted knee bent; other thigh raised toward torso with knee bent and foot off ground throughout. First pelvis on mat, then pelvis raised aligned with supporting thigh and torso. Arms resting beside body.”
- exercise_single_leg_calf_raise.png: “Two side-by-side full-body side views of supported single-leg calf raise: fingertips lightly touch stable support, one foot bears all weight and other foot lifted behind, then supporting heel raised while forefoot remains on floor. Nonworking foot never touches floor.”
- exercise_strides.png: “Three side-by-side full-body poses of relaxed running stride progression on flat ground. Natural running arm swing, upright torso slight forward lean, foot landing under hips. Controlled faster run, not all-out sprint. No text.”

Для сохранённых планов одноногие варианты разрешаются при отображении, без переимпорта или изменения дозировки. Общие изображения и планы сохранены.
