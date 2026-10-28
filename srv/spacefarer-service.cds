using { galactic } from '../db/schema';

@path: '/spacefarer'
@requires: 'SpacefarerUser'
service SpacefarerService {
  @odata.draft.enabled
  @restrict: [{ grant: '*', to: 'SpacefarerUser', where: (originPlanet.code = $user.planet) }]
  entity Spacefarers as projection on galactic.Spacefarers;

  @readonly
  @restrict: [{ grant: 'READ', to: 'SpacefarerUser', where: (code = $user.planet) }]
  entity Planets as projection on galactic.Planets;

  @readonly
  entity Departments as projection on galactic.Departments;

  @readonly
  entity Positions as projection on galactic.Positions;
}

annotate SpacefarerService.Spacefarers with {
  fullName @mandatory;
  email @mandatory;
  stardustCollection @assert.range: [0, 1000000];
  wormholeNavigationSkill @assert.range: [0, 100];
  navigationRank @readonly;
  spacesuitColor @mandatory;
  originPlanet @assert.target;
  department @assert.target;
  position @assert.target;
};
