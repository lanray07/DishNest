"""Export the local listing as paste-ready text. Does not contact any store."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def render(listing):
    sections = [
        ('Display title', listing['title']),
        ('Short description', listing['short_description']),
        ('Long description', listing['full_description']),
        ('Product feature bullets', '\n'.join(listing['feature_bullets'])),
        ('Keywords', ', '.join(listing['keywords'])),
        ('Release notes', listing['release_notes']),
        ('Website URL', listing.get('website_url')),
        ('Support URL', listing.get('support_contact')),
        ('Privacy policy URL (draft policy)', listing.get('privacy_policy_url')),
    ]
    text = 'DISHNEST - LOCAL STORE LISTING DRAFT\nNot entered in the console; not approved for submission.\nUpdated ' + listing['updated_at'] + '.\n\n'
    text += '\n\n'.join(label + '\n' + (value or 'Not configured') for label, value in sections)
    return text + '\n\nPublisher fields still required\n' + '\n'.join(listing['draft_notes']['required_publisher_inputs']) + '\n'


if __name__ == '__main__':
    listing = json.loads((ROOT / 'marketing/listing-en-GB.json').read_text(encoding='utf-8'))
    (ROOT / 'marketing/listing-en-GB.txt').write_text(render(listing), encoding='utf-8')
    print('Local store-listing text refreshed; no store contacted.')
