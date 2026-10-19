import unittest
from unittest.mock import patch
from fire_notification_setting import run


class NotificationUiFixture:
    def __init__(self, title='DishNest QA', locked=False, always_null=False, stuck_until_restart=False, own_settings=False, stuck_after_tap=False):
        self.title, self.locked, self.always_null = title, locked, always_null
        self.enabled = True
        self.taps = 0
        self.dumps = 0
        self.settled = False
        self.stuck_until_restart = stuck_until_restart
        self.own_settings = own_settings
        self.restarts = 0
        self.stuck_after_tap = stuck_after_tap

    def command(self, args, **kwargs):
        command = args[3:]
        if command[:3] == ['shell', 'dumpsys', 'window']:
            return b'mIsShowing=true' if self.locked else b'mIsShowing=false'
        if command[:3] == ['shell', 'am', 'start']:
            assert command[-1] == 'com.dishnest.app.qa'
        if command[:3] == ['shell', 'dumpsys', 'activity']:
            return b'mResumedActivity: ActivityRecord{fixture u0 com.android.settings/.Settings$AppNotificationSettingsActivity t1}' if self.own_settings else b''
        if command[:3] == ['shell', 'am', 'force-stop']:
            assert command[-1] == 'com.android.settings'
            self.restarts += 1
            self.stuck_until_restart = False
        if command[:3] == ['shell', 'uiautomator', 'dump']:
            self.dumps += 1
            if self.always_null or self.stuck_until_restart or not self.settled:
                self.settled = True
                return b'ERROR: null root node returned by UiTestAutomationBridge.'
            return b'UI hierarchy dumped to: /data/local/tmp/dishnest-qa-window.xml'
        if command[:2] == ['exec-out', 'cat']:
            assert self.settled
            return f'''<hierarchy>
              <node resource-id="com.android.settings:id/entity_header_title" text="{self.title}"/>
              <node resource-id="com.android.settings:id/switch_text" text="Show notifications"/>
              <node resource-id="com.android.settings:id/switch_widget" package="com.android.settings"
                checkable="true" enabled="true" checked="{str(self.enabled).lower()}" bounds="[1632,294][1728,354]"/>
            </hierarchy>'''.encode()
        if command[:3] == ['shell', 'input', 'tap']:
            assert command[3:] == ['1680', '324']
            self.enabled = not self.enabled
            self.settled = False
            self.taps += 1
            if self.stuck_after_tap:
                self.stuck_until_restart = True
        return b''


class FireNotificationSettingTest(unittest.TestCase):
    def execute(self, fixture, state):
        with patch('fire_notification_setting.subprocess.check_output', side_effect=fixture.command), patch('fire_notification_setting.time.sleep'):
            return run('fixture-device', state, 'adb')

    def test_null_root_before_and_after_tap_is_retried_without_duplicate_taps(self):
        fixture = NotificationUiFixture()
        result = self.execute(fixture, 'disabled')
        self.assertTrue(result['before'])
        self.assertFalse(result['enabled'])
        self.assertEqual(1, fixture.taps)
        self.assertEqual(4, fixture.dumps)
        self.assertTrue(self.execute(fixture, 'enabled')['enabled'])
        self.assertEqual(2, fixture.taps)

    def test_unexpected_package_title_never_changes_a_switch(self):
        fixture = NotificationUiFixture(title='An ordinary app')
        with self.assertRaisesRegex(ValueError, 'Expected the observed DishNest QA'):
            self.execute(fixture, 'disabled')
        self.assertEqual(0, fixture.taps)

    def test_lock_screen_never_starts_settings_or_changes_a_switch(self):
        fixture = NotificationUiFixture(locked=True)
        with self.assertRaisesRegex(ValueError, 'Unlock the Fire'):
            self.execute(fixture, 'disabled')
        self.assertEqual(0, fixture.dumps)
        self.assertEqual(0, fixture.taps)

    def test_persistent_null_root_stops_after_four_reads_without_a_tap(self):
        fixture = NotificationUiFixture(always_null=True)
        with self.assertRaisesRegex(ValueError, 'valid UI hierarchy'):
            self.execute(fixture, 'disabled')
        self.assertEqual(4, fixture.dumps)
        self.assertEqual(0, fixture.taps)

    def test_stuck_own_settings_is_recreated_once_before_the_verified_tap(self):
        fixture = NotificationUiFixture(stuck_until_restart=True, own_settings=True)
        result = self.execute(fixture, 'disabled')
        self.assertFalse(result['enabled'])
        self.assertTrue(result['settingsWindowRecovered'])
        self.assertEqual(1, fixture.restarts)
        self.assertEqual(1, fixture.taps)

    def test_even_own_settings_recovery_is_bounded_when_the_root_stays_null(self):
        fixture = NotificationUiFixture(always_null=True, own_settings=True)
        with self.assertRaisesRegex(ValueError, 'valid UI hierarchy'):
            self.execute(fixture, 'disabled')
        self.assertEqual(1, fixture.restarts)
        self.assertEqual(8, fixture.dumps)
        self.assertEqual(0, fixture.taps)

    def test_post_tap_window_recovery_checks_state_without_toggling_it_again(self):
        fixture = NotificationUiFixture(own_settings=True, stuck_after_tap=True)
        result = self.execute(fixture, 'disabled')
        self.assertFalse(result['enabled'])
        self.assertEqual(1, fixture.restarts)
        self.assertEqual(1, fixture.taps)

    def test_unrelated_foreground_is_never_restarted(self):
        fixture = NotificationUiFixture(stuck_until_restart=True)
        with self.assertRaisesRegex(ValueError, 'valid UI hierarchy'):
            self.execute(fixture, 'disabled')
        self.assertEqual(0, fixture.restarts)
        self.assertEqual(0, fixture.taps)


if __name__ == '__main__':
    unittest.main()
