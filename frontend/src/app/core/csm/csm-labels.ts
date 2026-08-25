import { CsmRating } from '../tickets/ticket.models';

export const CSM_LABEL: Record<CsmRating, string> = {
  SAD: 'Not Satisfied',
  NEUTRAL: 'Satisfied',
  HAPPY: 'Very Satisfied',
};

export const CSM_CHART_LABELS = [CSM_LABEL.SAD, CSM_LABEL.NEUTRAL, CSM_LABEL.HAPPY] as const;

export function csmLabel(rating: string | null | undefined): string {
  if (rating === 'SAD' || rating === 'NEUTRAL' || rating === 'HAPPY') {
    return CSM_LABEL[rating];
  }
  return '';
}
