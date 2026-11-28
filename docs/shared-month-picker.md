# Shared dashboard and meter month picker

Meter readings now uses `dialog_dashboard_month.xml` and the same month-grid implementation as the dashboard. `MeterMonthPickerDialog` is a small subclass that supplies only its result key, warning message and Phnom Penh current-period clock.

- The dashboard retains its result key, clock and default appearance. The optional message is hidden there.
- Meter readings retains `meter-month-result`, `meter-month-picker`, the existing unsaved-input/read-only/closed-period warning and its `Asia/Phnom_Penh` clock.
- Both use previous/next year controls, a twelve-month grid, This month, Cancel and Apply.
- This month changes the local selection; Apply confirms it. Cancel or dismiss does not emit a result or discard the form.
- Month/year selection is preserved through the shared picker's existing saved-state handling. Bounds remain 1900–9999, with a single-result guard.
- `MeterReadingFragment`, its ViewModel, reading saves, closed-period rules and current-month replacement rules are unchanged.

Removed `dialog_meter_month.xml` after verifying that the old layout, `DialogMeterMonthBinding`, and its unique NumberPicker IDs are unreferenced. There is one month-grid layout and implementation rather than two copied pickers.

Static checks cover the shared binding/layout ID, optional message, caller-specific routing/time zone, bounds/saved-selection/result guards, removal of obsolete references, XML validity and unchanged non-picker source files. Android resource/ViewBinding compilation and device tests remain pending.

On a device/emulator, test both dashboard and meter flows: select another month/year, cancel, use This month then Apply, rotate with a pending selection, and apply once. On the meter screen, verify the warning, saved-reading locks and current-month-only meter replacement behavior.
