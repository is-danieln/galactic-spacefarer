namespace galactic;

using { cuid, managed } from '@sap/cds/common';

entity Spacefarers : cuid, managed {
  fullName                : String(100) not null;
  email                   : String(254) not null;
  stardustCollection      : Integer default 0;
  wormholeNavigationSkill : Integer default 10;
  navigationRank          : String(20);
  spacesuitColor          : String(30) default 'White';
  originPlanet            : Association to Planets not null;
  department              : Association to Departments;
  position                : Association to Positions;
}

entity Planets {
  key code : String(10);
  name     : String(80);
}

entity Departments {
  key code : String(20);
  name     : String(80);
}

entity Positions {
  key code : String(20);
  name     : String(80);
}
